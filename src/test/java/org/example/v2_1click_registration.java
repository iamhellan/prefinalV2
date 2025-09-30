package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class v2_1click_registration {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(List.of("--start-maximized"))
        );
        context = browser.newContext(
                new Browser.NewContextOptions()
                        .setAcceptDownloads(true)
                        .setViewportSize(null)
        );
        page = context.newPage();
        page.setDefaultTimeout(15000);
    }

    @AfterAll
    static void tearDownAll() {
        try { if (context != null) context.close(); } catch (Throwable ignored) {}
        try { if (browser != null) browser.close(); } catch (Throwable ignored) {}
        try { if (playwright != null) playwright.close(); } catch (Throwable ignored) {}
        System.out.println("Тест завершён ✅ (браузер и контекст закрыты)");
    }

    // ---------- ХЕЛПЕРЫ ----------
    static void pauseShort() { pause(150); }
    static void pauseMedium() { pause(350); }
    static void pause(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) {} }

    static void clickIfVisible(Page page, String selector) {
        Locator loc = page.locator(selector);
        if (loc.count() > 0 && loc.first().isVisible()) {
            loc.first().click(new Locator.ClickOptions().setTimeout(5000));
            pauseShort();
        }
    }

    static void waitAndClick(Page page, String selector, int timeoutMs) {
        page.waitForSelector(selector,
                new Page.WaitForSelectorOptions().setTimeout(timeoutMs).setState(WaitForSelectorState.VISIBLE));
        page.locator(selector).first().click();
        pauseMedium();
    }

    static String randomPromo(int len) {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    static void waitForRegistrationModal(Page page) {
        page.waitForSelector("div.arcticmodal-container",
                new Page.WaitForSelectorOptions().setTimeout(15000).setState(WaitForSelectorState.VISIBLE));
    }

    static boolean isOneClickActive(Page page) {
        Locator tab = page.locator("button.c-registration__tab:has-text('В 1 клик')");
        if (tab.count() == 0) return false;
        Object res = tab.first().evaluate("el => el.classList.contains('active')");
        return Boolean.TRUE.equals(res);
    }

    static void jsClick(Locator loc) {
        if (loc.count() == 0) return;
        loc.first().dispatchEvent("click");
    }

    static void neutralizeOverlayIfNeeded(Page page) {
        page.evaluate("(() => {" +
                "const kill = sel => document.querySelectorAll(sel).forEach(n=>{try{n.style.pointerEvents='none'; n.style.zIndex='0';}catch(e){}});" +
                "kill('.arcticmodal-container_i2');" +
                "kill('.arcticmodal-container_i');" + // <-- ДОБАВИЛ ЭТОТ СЕЛЕКТОР!
                "kill('.v--modal-background-click');" +
                "kill('#modals-container *');" +
                "kill('.pf-main-container-wrapper-th-4 *');" +
                "})();");
    }

    static Path ensureDownloadsDir() throws Exception {
        Path downloads = Paths.get("downloads");
        if (!Files.exists(downloads)) Files.createDirectories(downloads);
        return downloads;
    }

    static boolean isLoggedOut(Page page) {
        boolean hasRegBtn = page.locator("button#registration-form-call").count() > 0
                && page.locator("button#registration-form-call").first().isVisible();
        boolean headerNotLogged = Boolean.TRUE.equals(page.evaluate("() => {" +
                "const h = document.querySelector('header.header');" +
                "return !!h && !h.classList.contains('header--user-logged');" +
                "}"));
        String url = page.url();
        boolean onPublicUrl = url.contains("1xbet.kz/") && !url.contains("/office/");
        return hasRegBtn || headerNotLogged || onPublicUrl;
    }

    static void waitUntilLoggedOutOrHeal(Page page) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (isLoggedOut(page)) return;
            neutralizeOverlayIfNeeded(page); clickIfVisible(page, "button.swal2-confirm.swal2-styled");
            neutralizeOverlayIfNeeded(page); clickIfVisible(page, "button.identification-popup-transition__close");
            neutralizeOverlayIfNeeded(page); clickIfVisible(page, "button.identification-popup-close");
            neutralizeOverlayIfNeeded(page);
            pause(300);
        }
        page.navigate("https://1xbet.kz/");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        long deadline2 = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline2) {
            if (isLoggedOut(page)) return;
            pause(300);
        }
    }

    // ---------- ТЕСТ ----------
    @Test
    void v2_registration() throws Exception {
        System.out.println("Открываем сайт 1xbet.kz (десктоп)");
        page.navigate("https://1xbet.kz/");
        pauseMedium();

        // --- ПЕРЕХОД: Платежи ---
        System.out.println("Переходим в раздел 'Платежи'");
        try {
            waitAndClick(page, "a.header-topbar-widgets-link--payments[href*='information/payment']", 12000);
        } catch (Throwable t) {
            System.out.println("Клик по ссылке 'Платежи' не удался, делаем прямой переход");
            page.navigate("https://1xbet.kz/information/payment");
        }
        page.waitForSelector("body", new Page.WaitForSelectorOptions()
                .setTimeout(12000).setState(WaitForSelectorState.VISIBLE));
        pauseShort();

        // --- РЕГИСТРАЦИЯ ---
        System.out.println("Жмём 'Регистрация'");
        waitAndClick(page, "button#registration-form-call", 15000);

        System.out.println("Ожидаем модалку регистрации");
        waitForRegistrationModal(page);
        pauseShort();

        clickIfVisible(page, "button:has-text('Ок')");

        if (!isOneClickActive(page)) {
            System.out.println("Активируем вкладку 'В 1 клик'");
            Locator oneClickTab = page.locator("div.arcticmodal-container button.c-registration__tab:has-text('В 1 клик')");
            if (oneClickTab.count() == 0) {
                oneClickTab = page.locator("div.arcticmodal-container button.c-registration__tab:has(svg.c-registration__tab-ico)");
            }
            try {
                oneClickTab.first().click(new Locator.ClickOptions().setTimeout(3000));
            } catch (Throwable ignore) {
                neutralizeOverlayIfNeeded(page);
                jsClick(oneClickTab);
            }
            pauseShort();
        } else {
            System.out.println("Вкладка 'В 1 клик' уже активна");
        }

        // Промокод
        System.out.println("Вводим рандомный промокод");
        String promo = randomPromo(8);
        Locator promoInput = page.locator("input#popup_registration_ref_code");
        if (promoInput.count() > 0 && promoInput.first().isVisible()) {
            promoInput.first().fill(promo);
        } else {
            page.fill("input[placeholder*='промокод' i]", promo);
        }
        pauseShort();

        // Бонусы
        System.out.println("Отказываемся от бонусов, затем соглашаемся");
        clickIfVisible(page, "div.c-registration-bonus__item.c-registration-bonus__item--close:has(.c-registration-bonus__title:has-text('Отказаться'))");
        clickIfVisible(page, "div.c-registration-bonus__item:has(.c-registration-bonus__title:has-text('Принять'))");

        // Зарегистрироваться
        System.out.println("Жмём 'Зарегистрироваться'");
        waitAndClick(page, "div.c-registration__button.submit_registration:has-text('Зарегистрироваться')", 15000);

        // --- БЕЗ фиксированной паузы: ждём блок копирования ---
        System.out.println("Ждём блок копирования логина/пароля (до 120 сек)");
        page.waitForSelector("#js-post-reg-copy-login-password",
                new Page.WaitForSelectorOptions().setTimeout(120_000).setState(WaitForSelectorState.VISIBLE));

        // ----------- POST-REGISTRATION FLOW -------------
        System.out.println("Кликаем 'Копировать'");
        clickIfVisible(page, "#js-post-reg-copy-login-password");
        pauseMedium();

        System.out.println("Кликаем 'Сохранить в файл'");
        clickIfVisible(page, "a#account-info-button-file");
        pauseMedium();

        System.out.println("Кликаем 'Сохранить картинкой'");
        clickIfVisible(page, "a#account-info-button-image");
        pauseMedium();

        System.out.println("Кликаем 'Выслать на e-mail'");
        clickIfVisible(page, "a#form_mail_after_submit");
        pauseMedium();

        // Вводим email
        Locator emailField = page.locator("input.post-email__input[type='email']:visible").first();
        emailField.fill("zhante1111@gmail.com");
        pauseShort();

        Locator sendBtn = page.locator("button.js-post-email-content-form__btn:not([disabled])");
        sendBtn.waitFor();
        sendBtn.click();
        System.out.println("Email отправлен");
        pauseMedium();

        // Кликаем "Получить бонус" (overlay теперь не мешает)
        System.out.println("Кликаем 'Получить бонус'");
        neutralizeOverlayIfNeeded(page); // вот здесь overlay, мешающий клику
        clickIfVisible(page, "#form_get_bonus_after_submit");
        pauseMedium();

        // Уже в личном кабинете, закрываем возможное всплывающее окно
        neutralizeOverlayIfNeeded(page); clickIfVisible(page, "button.identification-popup-close");
        neutralizeOverlayIfNeeded(page); clickIfVisible(page, "div#closeModal");
        neutralizeOverlayIfNeeded(page); clickIfVisible(page, "button[title='Закрыть'], button[aria-label='Закрыть']");
        neutralizeOverlayIfNeeded(page); clickIfVisible(page, "button.reset-password__close");
        pauseShort();

        // --- Дальше идём по старому сценарию ---
        // Выходим из аккаунта
        System.out.println("Кликаем 'Выход'");
        neutralizeOverlayIfNeeded(page); clickIfVisible(page, "a.ap-left-nav__item_exit");
        pauseShort();

        // Подтверждаем выход 'ОК'
        neutralizeOverlayIfNeeded(page); clickIfVisible(page, "button.swal2-confirm.swal2-styled");
        System.out.println("Вышли из аккаунта");

        // Финальный чек — разлогинились?
        waitUntilLoggedOutOrHeal(page);

        System.out.println("Тест регистрации в 1 клик полностью завершён! 🚀");
    }
}
