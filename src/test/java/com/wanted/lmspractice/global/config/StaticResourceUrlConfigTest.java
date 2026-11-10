package com.wanted.lmspractice.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.resource.ResourceUrlProvider;
import org.springframework.web.servlet.resource.ResourceUrlProviderExposingInterceptor;
import org.springframework.web.util.UrlPathHelper;

/**
 * [역할] 성능 개선(4단계) — 정적 리소스 링크만 버전 주소로 바꾸고, 일반 화면 링크는 버전 확인(ResourceUrlProvider)을 건너뛰는지 확인한다
 * [전제] DB·Spring 컨텍스트 없이 Mock 요청·응답 + 가짜 ResourceUrlProvider(정적 파일 버전 주소 계산기)
 */
class StaticResourceUrlConfigTest {

    private List<String> encode(String contextPath, ResourceUrlProvider provider, String... urls) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", contextPath + "/courses");
        request.setContextPath(contextPath);
        List<String> encoded = new ArrayList<>();
        new StaticResourceUrlConfig.StaticOnlyResourceUrlEncodingFilter().doFilter(request, new MockHttpServletResponse(),
                (req, res) -> {
                    // 실제 앱에서는 ResourceUrlProviderExposingInterceptor가 필터 뒤에서 "감싼 요청"에 이 값을 넣는다 — 같은 시점을 흉내 낸다
                    req.setAttribute(ResourceUrlProviderExposingInterceptor.RESOURCE_URL_PROVIDER_ATTR, provider);
                    for (String url : urls) {
                        encoded.add(((HttpServletResponse) res).encodeURL(url));
                    }
                });
        return encoded;
    }

    @SuppressWarnings("removal")   // Spring 7 ResourceUrlEncodingFilter가 아직 이 메서드로 경로를 계산하므로 가짜에도 같은 값을 준다
    private ResourceUrlProvider provider() {
        ResourceUrlProvider provider = mock(ResourceUrlProvider.class);
        when(provider.getUrlPathHelper()).thenReturn(UrlPathHelper.defaultInstance);
        when(provider.getForLookupPath("/css/tw.css")).thenReturn("/css/tw-66ea.css");
        when(provider.getForLookupPath("/images/a.png")).thenReturn("/images/a-1234.png");
        when(provider.getForLookupPath("/js/work-like.js")).thenReturn("/js/work-like-9f9f.js");
        return provider;
    }

    @Test
    @DisplayName("정적 리소스(/css·/images·/js) 링크는 기본 필터와 같이 내용 해시 주소로 바뀐다")
    void staticLinks_versioned() throws Exception {
        ResourceUrlProvider provider = provider();

        assertThat(encode("", provider, "/css/tw.css", "/images/a.png", "/js/work-like.js?x=1"))
                .containsExactly("/css/tw-66ea.css", "/images/a-1234.png", "/js/work-like-9f9f.js?x=1");
    }

    @Test
    @DisplayName("일반 화면 링크(/courses/1 등)는 그대로 두고 버전 확인을 하지 않는다 — 요청마다 정적 파일을 찾던 비용 제거")
    void otherLinks_untouched_noLookup() throws Exception {
        ResourceUrlProvider provider = provider();

        assertThat(encode("", provider, "/courses/1", "/hobbies?categoryId=1", "/files/works/3", "/"))
                .containsExactly("/courses/1", "/hobbies?categoryId=1", "/files/works/3", "/");
        verify(provider, never()).getForLookupPath(anyString());
    }

    @Test
    @DisplayName("컨텍스트 경로(/app)가 있어도 정적 링크만 바뀐다")
    void contextPath() throws Exception {
        ResourceUrlProvider provider = provider();

        assertThat(encode("/app", provider, "/app/css/tw.css", "/app/courses/1"))
                .containsExactly("/app/css/tw-66ea.css", "/app/courses/1");
        verify(provider, times(1)).getForLookupPath(anyString());
    }
}
