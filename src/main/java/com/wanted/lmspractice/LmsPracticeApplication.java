package com.wanted.lmspractice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LmsPracticeApplication {

    public static void main(String[] args) {
        // [성능 개선] 커넥션 풀(Hikari)은 0.5초 넘게 쉰 커넥션을 꺼낼 때마다 DB에 "살아 있나" 확인(ping)을 보낸다.
        // 사람이 화면을 넘기는 간격은 거의 항상 0.5초보다 길어서, 원격 공용 DB에서는 화면마다 왕복 1번(100ms 이상)이 더 들었다.
        // 30초 안에 쓴 커넥션은 확인 없이 쓰고, 오래 쉰 커넥션은 application.yaml 의 hikari.keepalive-time 이 미리 확인해 둔다.
        // (application.yaml 로는 줄 수 없는 Hikari 시스템 속성이라 여기서 넣는다. 실행 인자 -D 로 따로 주면 그 값을 쓴다)
        if (System.getProperty("com.zaxxer.hikari.aliveBypassWindowMs") == null) {
            System.setProperty("com.zaxxer.hikari.aliveBypassWindowMs", "30000");
        }
        SpringApplication.run(LmsPracticeApplication.class, args);
    }

}
