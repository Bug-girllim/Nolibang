#!/usr/bin/env bash
# ==================================================
# 운영 복원 — scripts/backup.sh 가 만든 백업 폴더 하나를 DB 와 업로드 폴더에 되돌린다
# ==================================================
# 사용: scripts/restore.sh backups/nolibang-20261111-030000
#   앱을 먼저 멈춘다(복원 중 들어온 요청이 섞이지 않게). 끝나면 앱을 다시 띄운다 — 로그인 세션은 메모리라 모두 다시 로그인한다.
# 환경변수는 backup.sh 와 같다 (DB_URL·DB_USERNAME·DB_PASSWORD·UPLOAD_DIR·MYSQL_BIN·DB_SSL_MODE).
#   RESTORE_DB   다른 DB 이름으로 복원할 때(복원 연습용 빈 DB 등). 없으면 DB_URL 의 DB 이름
#   RESTORE_YES=1  확인 질문 없이 진행 (자동화용)
# 주의: 대상 DB 의 표를 백업 내용으로 덮어쓴다. 업로드 파일은 덮어쓰기만 하고, 백업에 없는 파일은 지우지 않는다.
set -euo pipefail

src="${1:-}"
if [[ -z "$src" || ! -f "$src/db.sql.gz" || ! -f "$src/uploads.tar.gz" ]]; then
  echo "사용: $0 <백업 폴더> (db.sql.gz 와 uploads.tar.gz 가 있어야 합니다)" >&2
  exit 1
fi

DB_URL="${DB_URL:-jdbc:mysql://localhost:3306/nolibang}"
DB_USERNAME="${DB_USERNAME:-wanted}"
DB_PASSWORD="${DB_PASSWORD:-wanted}"
UPLOAD_DIR="${UPLOAD_DIR:-./uploads}"
MYSQL="${MYSQL_BIN:+$MYSQL_BIN/}mysql"

rest="${DB_URL#jdbc:mysql://}"
hostport="${rest%%/*}"
url_db="${rest#*/}"; url_db="${url_db%%\?*}"
host="${hostport%%:*}"
port="3306"; [[ "$hostport" == *:* ]] && port="${hostport##*:}"
backup_db="$(sed -n 's/^db=\([^ ]*\).*/\1/p' "$src/manifest.txt" 2>/dev/null || true)"
target_db="${RESTORE_DB:-$url_db}"

echo "백업:   $src"
cat "$src/manifest.txt" 2>/dev/null || true
echo "대상:   $host:$port/$target_db, 업로드 폴더 $UPLOAD_DIR"
if [[ "${RESTORE_YES:-}" != "1" ]]; then
  read -r -p "대상 DB 이름($target_db)을 그대로 입력하면 복원합니다: " answer
  [[ "$answer" == "$target_db" ]] || { echo "취소했습니다."; exit 1; }
fi

cnf="$(mktemp)"
trap 'rm -f "$cnf"' EXIT
chmod 600 "$cnf"
printf '[client]\nuser=%s\npassword=%s\nhost=%s\nport=%s\n' "$DB_USERNAME" "$DB_PASSWORD" "$host" "$port" > "$cnf"
ssl_opt=()
[[ -n "${DB_SSL_MODE:-}" ]] && ssl_opt=(--ssl-mode="$DB_SSL_MODE")

echo "[$(date '+%F %T')] ① DB 복원"
# 덤프에는 "CREATE DATABASE / USE 원래이름" 이 들어 있다 — 다른 이름으로 복원할 때는 그 두 줄만 대상 이름으로 바꾼다
gzip -dc "$src/db.sql.gz" \
  | sed -e "s/^CREATE DATABASE \/\*!32312 IF NOT EXISTS\*\/ \`$backup_db\`/CREATE DATABASE \/*!32312 IF NOT EXISTS*\/ \`$target_db\`/" \
        -e "s/^USE \`$backup_db\`;/USE \`$target_db\`;/" \
  | "$MYSQL" --defaults-extra-file="$cnf" "${ssl_opt[@]}" --default-character-set=utf8mb4

echo "[$(date '+%F %T')] ② 업로드 파일 복원 → $UPLOAD_DIR"
mkdir -p "$UPLOAD_DIR"
tar -xzf - -C "$UPLOAD_DIR" < "$src/uploads.tar.gz"

echo "[$(date '+%F %T')] 완료. 앱을 다시 띄운 뒤 작품 이미지·업로드 영상이 열리는지 확인하세요."
