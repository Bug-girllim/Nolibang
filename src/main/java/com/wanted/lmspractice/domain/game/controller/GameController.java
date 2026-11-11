package com.wanted.lmspractice.domain.game.controller;

import com.wanted.lmspractice.domain.auth.dto.AuthDetails;
import com.wanted.lmspractice.domain.game.service.GameService;
import com.wanted.lmspractice.domain.game.service.GameService.BadgeEquipResult;
import com.wanted.lmspractice.domain.game.service.GameService.EquipResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * [역할] RPG 화면 — 내 캐릭터(레벨·직업·보스전·퀘스트·꾸미기)와 길드 순위
 * [담당] RPG 성장 요소   [화면] game/character.html, game/guild.html
 * [연결]
 *  - 호출받음: 브라우저(GET /my/character, POST /my/character/quests/claim, POST /my/character/equip, POST /my/character/badges/equip·unequip, GET /my/guild)
 *             — SecurityConfig 가 /my/** 를 USER 역할만 허용한다
 *  - 호출함:   GameService
 *  - 이동:     보스 카드 → /assignments?enrollmentId=, /assignments/{reviewRoundId}/result / 상단 탭 → 캐릭터·길드
 *  - 화면:     장착·해제 폼은 static/js/character.js 가 가로채서 새로고침 없이 처리한다(fetch + 헤더 X-Requested-With). 스크립트가 꺼진 환경에서는
 *             폼이 그대로 전송되어 아래 redirect 방식(flash 안내)으로 똑같이 동작한다.
 * [규칙] 본인 것만 — 조회·저장 대상은 로그인한 사용자 id 뿐이고, 요청에서 다른 사람의 id 를 받지 않는다.
 *        변경 요청은 POST 후 redirect(CONVENTIONS §5), 결과 안내는 flash message / errorMessage.
 *        새로고침 없는 요청(헤더 X-Requested-With: XMLHttpRequest)은 redirect 대신 JSON {success, message} 를 돌려준다 — 안내 문구는 같은 것을 쓴다.
 * [기술] 🟢 @Controller, @GetMapping, @PostMapping, @RequestParam, RedirectAttributes, @AuthenticationPrincipal
 *        🟡 미학습 문법: @ResponseBody + 요청 헤더 조건(headers=) — 같은 URL 을 화면 이동용/JSON 용으로 나누려고 사용
 */
@Controller
@RequestMapping("/my")
@RequiredArgsConstructor
public class GameController {

    private static final String AJAX_HEADER = "X-Requested-With=XMLHttpRequest";

    private final GameService gameService;

    /** 새로고침 없는 요청의 응답 — success 는 처리 성공 여부, message 는 화면에 띄울 안내 */
    public record ActionResult(boolean success, String message) {
    }

    /** 내 캐릭터. 모델: page(CharacterPageDTO) */
    @GetMapping("/character")
    public String character(@AuthenticationPrincipal AuthDetails details, Model model) {
        model.addAttribute("page", gameService.getCharacterPage(details.getUserId(), details.getLoginUser().getNickname()));
        return "game/character";
    }

    /** 퀘스트 보상 받기 */
    @PostMapping("/character/quests/claim")
    public String claimQuest(@RequestParam String key,
                             @AuthenticationPrincipal AuthDetails details,
                             RedirectAttributes redirectAttributes) {
        switch (gameService.claimQuest(details.getUserId(), key)) {
            case CLAIMED -> redirectAttributes.addFlashAttribute("message", "퀘스트 보상을 받았어요! 경험치가 올랐어요.");
            case NOT_ENOUGH -> redirectAttributes.addFlashAttribute("errorMessage", "아직 퀘스트를 달성하지 못했어요.");
            case ALREADY_CLAIMED -> redirectAttributes.addFlashAttribute("errorMessage", "이번 기간 보상은 이미 받았어요.");
        }
        return "redirect:/my/character";
    }

    // ---------------------------------------------------------------
    // 꾸미기 장착
    // ---------------------------------------------------------------

    /** 꾸미기 아이템 장착 (화면 이동 방식) */
    @PostMapping("/character/equip")
    public String equip(@RequestParam String slot,
                        @RequestParam String code,
                        @AuthenticationPrincipal AuthDetails details,
                        RedirectAttributes redirectAttributes) {
        return redirectWith(redirectAttributes, equipResult(gameService.equip(details.getUserId(), slot, code)));
    }

    /** 꾸미기 아이템 장착 (새로고침 없는 방식 — JSON) */
    @PostMapping(value = "/character/equip", headers = AJAX_HEADER)
    @ResponseBody
    public ActionResult equipAjax(@RequestParam String slot,
                                  @RequestParam String code,
                                  @AuthenticationPrincipal AuthDetails details) {
        return equipResult(gameService.equip(details.getUserId(), slot, code));
    }

    // ---------------------------------------------------------------
    // 대표 배지 장착·해제 (최대 GameService.BADGE_SLOT_MAX 칸)
    // ---------------------------------------------------------------

    /** 대표 배지 장착 (화면 이동 방식) */
    @PostMapping("/character/badges/equip")
    public String equipBadge(@RequestParam String code,
                             @AuthenticationPrincipal AuthDetails details,
                             RedirectAttributes redirectAttributes) {
        return redirectWith(redirectAttributes, badgeEquipResult(gameService.equipBadge(details.getUserId(), code)));
    }

    /** 대표 배지 장착 (새로고침 없는 방식 — JSON) */
    @PostMapping(value = "/character/badges/equip", headers = AJAX_HEADER)
    @ResponseBody
    public ActionResult equipBadgeAjax(@RequestParam String code, @AuthenticationPrincipal AuthDetails details) {
        return badgeEquipResult(gameService.equipBadge(details.getUserId(), code));
    }

    /** 대표 배지 해제 (화면 이동 방식) */
    @PostMapping("/character/badges/unequip")
    public String unequipBadge(@RequestParam String code,
                               @AuthenticationPrincipal AuthDetails details,
                               RedirectAttributes redirectAttributes) {
        gameService.unequipBadge(details.getUserId(), code);
        return redirectWith(redirectAttributes, new ActionResult(true, "대표 배지를 해제했어요."));
    }

    /** 대표 배지 해제 (새로고침 없는 방식 — JSON) */
    @PostMapping(value = "/character/badges/unequip", headers = AJAX_HEADER)
    @ResponseBody
    public ActionResult unequipBadgeAjax(@RequestParam String code, @AuthenticationPrincipal AuthDetails details) {
        gameService.unequipBadge(details.getUserId(), code);
        return new ActionResult(true, "대표 배지를 해제했어요.");
    }

    /** 길드(분반) 순위. 모델: guilds(GuildPageDTO) */
    @GetMapping("/guild")
    public String guild(@AuthenticationPrincipal AuthDetails details, Model model) {
        model.addAttribute("guilds", gameService.getGuildPage(details.getUserId()));
        return "game/guild";
    }

    // ---------------------------------------------------------------
    // 안내 문구 — 화면 이동 방식과 JSON 방식이 같은 문구를 쓴다
    // ---------------------------------------------------------------

    private ActionResult equipResult(EquipResult result) {
        return switch (result) {
            case EQUIPPED -> new ActionResult(true, "꾸미기를 적용했어요.");
            case LOCKED -> new ActionResult(false, "아직 해금되지 않은 아이템이에요.");
        };
    }

    private ActionResult badgeEquipResult(BadgeEquipResult result) {
        return switch (result) {
            case EQUIPPED -> new ActionResult(true, "대표 배지로 장착했어요.");
            case ALREADY_EQUIPPED -> new ActionResult(false, "이미 장착한 배지예요.");
            case FULL -> new ActionResult(false, "대표 배지 " + GameService.BADGE_SLOT_MAX
                    + "칸이 모두 찼어요. 다른 배지를 장착하려면 먼저 장착 중인 배지를 하나 해제해 주세요.");
            case NOT_OWNED -> new ActionResult(false, "아직 받지 못한 배지예요.");
        };
    }

    private String redirectWith(RedirectAttributes redirectAttributes, ActionResult result) {
        redirectAttributes.addFlashAttribute(result.success() ? "message" : "errorMessage", result.message());
        return "redirect:/my/character";
    }
}
