#!/usr/bin/env bash
# ==================================================
# 운영 백업 — DB 와 업로드 폴더를 "같은 시점"으로 묶어 한 폴더에 저장한다
# ==================================================
# 순서가 중요하다: ① DB 덤프(--single-transaction 으로 한 순간의 스냅숏) → ② 업로드 파일 압축.
#   DB 를 먼저 찍으면, DB 에 기록된 이미지·영상 파일은 그 뒤에 압축하는 파일 묶음에 반드시 들어 있다
#   (반대 순서면 사이에 올라온 작품의 DB 행은 있는데 파일이 없는 백업이 생긴다). 남는 쪽은 "행 없는 파일"뿐이라 복원해도 화면은 깨지지 않는다.
#
# 쓰는 환경변수 (앱 prod 프로필과 같은 이름 — 따로 비밀번호를 적지 않는다)
#   DB_URL        jdbc:mysql://호스트:포트/DB이름?... (없으면 로컬 jdbc:mysql://localhost:3306/nolibang)
#   DB_USERNAME   DB_PASSWORD
#   UPLOAD_DIR    앱의 file.upload-dir 과 같은 폴더 (없으면 ./uploads)
#   BACKUP_DIR    백업을 쌓을 폴더 (없으면 ./backups) — 가능하면 서버 디스크가 아닌 다른 디스크·외부 저장소
#   BACKUP_KEEP   남길 백업 개수 (없으면 14, 오래된 것부터 지움)
#   MYSQL_BIN     mysqldump 가 PATH 에 없을 때 그 폴더 (예: "/c/Program Files/MySQL/MySQL Server 8.0/bin")
#   DB_SSL_MODE   원격 DB 가 TLS 를 요구하면 REQUIRED (Aiven 등)
#
# 백업 대상 파일: UPLOAD_DIR/works/ (작품·과제 이미지), UPLOAD_DIR/videos/uploaded/ (강사·관리자 업로드 영상).
#   시드 파일(uploads/seed, uploads/videos 의 시연 영상)은 Git 저장소에 있으므로 넣지 않는다.
#
# 예) 매일 새벽 3시 (리눅스 crontab):  0 3 * * * cd /srv/nolibang && ./scripts/backup.sh >> backups/backup.log 2>&1
# 복원: scripts/restore.sh <백업 폴더>
set -euo pipefail

DB_URL="${DB_URL:-jdbc:mysql://localhost:3306/nolibang}"
DB_USERNAME="${DB_USERNAME:-wanted}"
DB_PASSWORD="${DB_PASSWORD:-wanted}"
UPLOAD_DIR="${UPLOAD_DIR:-./uploads}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
BACKUP_KEEP="${BACKUP_KEEP:-14}"
DUMP="${MYSQL_BIN:+$MYSQL_BIN/}mysqldump"

# jdbc:mysql://host:port/db?params → host, port, db
rest="${DB_URL#jdbc:mysql://}"
hostport="${rest%%/*}"
db="${rest#*/}"; db="${db%%\?*}"
host="${hostport%%:*}"
port="3306"; [[ "$hostport" == *:* ]] && port="${hostport##*:}"
if [[ -z "$host" || -z "$db" || "$rest" == "$DB_URL" ]]; then
  echo "DB_URL 형식을 읽지 못했습니다: jdbc:mysql://호스트:포트/DB이름 형태여야 합니다" >&2
  exit 1
fi

stamp="$(date +%Y%m%d-%H%M%S)"
target="$BACKUP_DIR/$db-$stamp"
mkdir -p "$target"

# 비밀번호를 명령줄(프로세스 목록)에 남기지 않도록 임시 설정 파일로 넘긴다
cnf="$(mktemp)"
trap 'rm -f "$cnf"' EXIT
chmod 600 "$cnf"
printf '[client]\nuser=%s\npassword=%s\nhost=%s\nport=%s\n' "$DB_USERNAME" "$DB_PASSWORD" "$host" "$port" > "$cnf"

ssl_opt=()
[[ -n "${DB_SSL_MODE:-}" ]] && ssl_opt=(--ssl-mode="$DB_SSL_MODE")

echo "[$(date '+%F %T')] ① DB 덤프: $host:$port/$db"
# --single-transaction: 잠금 없이 한 순간의 스냅숏(InnoDB). 뷰·트리거·생성 열·CHECK 포함.
# --set-gtid-purged=OFF·--no-tablespaces: 관리형 DB(Aiven)에서 권한 문제 없이 덤프·복원되게 한다.
"$DUMP" --defaults-extra-file="$cnf" "${ssl_opt[@]}" \
  --single-transaction --routines --triggers --set-gtid-purged=OFF --no-tablespaces \
  --default-character-set=utf8mb4 --databases "$db" \
  | gzip > "$target/db.sql.gz"

echo "[$(date '+%F %T')] ② 업로드 파일: $UPLOAD_DIR (works/, videos/uploaded/)"
parts=()
[[ -d "$UPLOAD_DIR/works" ]] && parts+=(works)
[[ -d "$UPLOAD_DIR/videos/uploaded" ]] && parts+=(videos/uploaded)
if (( ${#parts[@]} > 0 )); then
  tar -czf - -C "$UPLOAD_DIR" "${parts[@]}" > "$target/uploads.tar.gz"   # 표준 출력으로 써서 윈도우 경로(C:)를 원격 호스트로 읽는 문제를 피한다
else
  tar -czf - -T /dev/null > "$target/uploads.tar.gz"  # 올린 파일이 아직 없어도 빈 묶음을 남겨 복원 절차를 같게 한다
fi

{
  echo "db=$db host=$host port=$port"
  echo "created=$stamp"
  echo "upload_dir=$UPLOAD_DIR"
  echo "db_bytes=$(wc -c < "$target/db.sql.gz")"
  echo "upload_files=$(tar -tzf - < "$target/uploads.tar.gz" | grep -vc '/$' || true)"
} > "$target/manifest.txt"

# 덤프가 끝까지 쓰였는지 확인 (mysqldump 는 정상 종료 시 마지막 줄에 "Dump completed" 를 쓴다)
if ! gzip -dc "$target/db.sql.gz" | tail -n 1 | grep -q "Dump completed"; then
  echo "DB 덤프가 끝까지 쓰이지 않았습니다: $target/db.sql.gz" >&2
  exit 1
fi

echo "[$(date '+%F %T')] 완료: $target"
cat "$target/manifest.txt"

# 오래된 백업 정리 (같은 DB 이름의 백업만, 최신 BACKUP_KEEP 개 남김)
ls -1d "$BACKUP_DIR/$db-"* 2>/dev/null | sort | head -n -"$BACKUP_KEEP" | while read -r old; do
  echo "오래된 백업 삭제: $old"
  rm -rf "$old"
done
