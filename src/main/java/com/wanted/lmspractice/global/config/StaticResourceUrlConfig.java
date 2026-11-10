package com.wanted.lmspractice.global.config;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import org.springframework.boot.autoconfigure.web.ConditionalOnEnabledResourceChain;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.resource.ResourceUrlEncodingFilter;

/**
 * [역할] 정적 파일 내용 해시 버전(#82, application.yaml 4-1)을 쓸 때, 화면 링크 중 정적 리소스(/css/·/js/·/images/)만 버전 주소로 바꾼다 — 성능 개선 4단계
 * [담당] 공통(성능 개선)   [연결] application.yaml 4-1(spring.web.resources.chain — #82), templates의 th:href·th:src="@{…}"
 * [왜 필요한가]
 *  - 파일 버전 관리를 켜면 Spring Boot가 ResourceUrlEncodingFilter를 걸어, 화면의 모든 @{…} 링크를 "정적 파일인지" 확인한다.
 *  - 정적 파일 링크는 결과가 캐시되지만, /courses/1 같은 일반 화면 링크는 "정적 파일 아님(null)" 결과가 캐시되지 않아
 *    요청마다 정적 파일 위치 4곳(classpath)을 다시 찾는다(버전 대상 경로를 /css·/js·/images로 좁혀도 마찬가지 — 버전 계산 앞의
 *    "정적 파일 찾기" 단계에서 생기는 비용이다). 강의 목록(/courses)은 링크 43개 중 32개가 일반 링크라
 *    화면 하나에 약 10ms가 더 들었다(측정: 버전 관리 끔 9.5ms → 켬 20.2ms).
 *  - 이 필터는 같은 ResourceUrlEncodingFilter를 쓰되, 정적 리소스 경로가 아닌 링크는 버전 확인을 건너뛰고 원래 응답으로 바로 보낸다.
 *    정적 리소스 링크의 결과(내용 해시 주소)는 기본 필터와 같다.
 * [조건] spring.web.resources.chain이 켜졌을 때만 등록된다 — #82로 모든 프로필에서 켜져 있으므로 로컬 개발·배포 모두 적용된다.
 *        같은 종류의 필터를 등록했으므로 Spring Boot 기본 필터(ThymeleafAutoConfiguration.resourceUrlEncodingFilter)는 등록되지 않는다.
 * [기술] 🟡 미학습 기술: FilterRegistrationBean·HttpServletResponseWrapper — Spring Boot 기본 필터의 동작을 정적 경로로만 좁히기 위해 사용
 */
@Configuration(proxyBeanMethods = false)
public class StaticResourceUrlConfig {

    /** 정적 리소스 경로 — SecurityConfig·WebConfig의 정적 리소스 경로와 같다 */
    static final String[] STATIC_PREFIXES = {"/css/", "/js/", "/images/"};

    /** Spring Boot 기본 등록과 같은 디스패처(REQUEST·ERROR)로 등록한다 */
    @Bean
    @ConditionalOnEnabledResourceChain
    FilterRegistrationBean<ResourceUrlEncodingFilter> resourceUrlEncodingFilter() {
        FilterRegistrationBean<ResourceUrlEncodingFilter> registration =
                new FilterRegistrationBean<>(new StaticOnlyResourceUrlEncodingFilter());
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR);
        return registration;
    }

    /** ResourceUrlEncodingFilter(버전 주소 변환)를 정적 리소스 링크에만 적용하는 필터 */
    static class StaticOnlyResourceUrlEncodingFilter extends ResourceUrlEncodingFilter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse)) {
                super.doFilter(request, response, filterChain);
                return;
            }
            String contextPath = httpRequest.getContextPath();
            // 부모 필터가 버전 변환 응답(versioning)을 만들어 넘기면, 그 바깥을 한 번 더 감싸 정적 링크만 versioning으로 보낸다
            super.doFilter(request, response, (req, versioning) ->
                    filterChain.doFilter(req, new StaticOnlyResponseWrapper((HttpServletResponse) versioning, contextPath)));
        }
    }

    /** encodeURL: 정적 리소스 링크 → 버전 변환 응답, 그 밖의 링크 → 원래 응답(버전 확인 없이) */
    static class StaticOnlyResponseWrapper extends HttpServletResponseWrapper {

        private final HttpServletResponse versioning;
        private final HttpServletResponse plain;
        private final String contextPath;

        StaticOnlyResponseWrapper(HttpServletResponse versioning, String contextPath) {
            super(versioning);
            this.versioning = versioning;
            this.plain = versioning instanceof HttpServletResponseWrapper wrapper
                    ? (HttpServletResponse) wrapper.getResponse() : versioning;
            this.contextPath = contextPath == null ? "" : contextPath;
        }

        @Override
        public String encodeURL(String url) {
            return isStaticResource(url) ? versioning.encodeURL(url) : plain.encodeURL(url);
        }

        private boolean isStaticResource(String url) {
            if (url == null || !url.startsWith(contextPath)) {
                return false;
            }
            String path = url.substring(contextPath.length());
            for (String prefix : STATIC_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }
}
