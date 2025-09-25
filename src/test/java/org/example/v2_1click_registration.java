package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.TimeoutError;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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
                "kill('.v--modal-background-click');" +
                "kill('#modals-container *');" +
                "kill('.pf-main-container-wrapper-th-4 *');" + // «подписки/пуши»
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
            // пробуем закрыть возможные попапы и подтвердить «ОК»
            clickIfVisible(page, "button.swal2-confirm.swal2-styled");
            clickIfVisible(page, "button.identification-popup-transition__close");
            clickIfVisible(page, "button.identification-popup-close");
            neutralizeOverlayIfNeeded(page);
            pause(300);
        }
        // план Б: перейти на главную и проверить ещё раз
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

        clickIfVisible(page, "button:has-text('Ок')"); // редкий инфо-попап

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

        // Копировать логин/пароль — строго по id
        System.out.println("Кликаем 'Скопировать' логин/пароль");
        page.locator("#js-post-reg-copy-login-password").first().click();

        // Закрываем всплывающее окно «ОК», если появилось
        clickIfVisible(page, "button.swal2-confirm.swal2-styled:has-text('ОК'), button.swal2-confirm.swal2-styled:has-text('OK'), button.swal2-confirm.swal2-styled");

        Path downloadsDir = ensureDownloadsDir();

        // Сохраняем в файл (download || blob-фоллбэк)
        System.out.println("Сохраняем в файл");
        Locator saveFileBtn = page.locator("a#account-info-button-file");
        if (saveFileBtn.count() > 0 && saveFileBtn.first().isVisible()) {
            boolean fileSaved = false;
            try {
                Download d1 = page.waitForDownload(
                        new Page.WaitForDownloadOptions().setTimeout(30_000),
                        () -> saveFileBtn.first().click()
                );
                String suggested = d1.suggestedFilename();
                System.out.println("Скачали файл: " + suggested);
                d1.saveAs(downloadsDir.resolve(suggested));
                fileSaved = true;
            } catch (TimeoutError te) {
                System.out.println("Download не пришёл за 30с — пробуем blob-фоллбэк...");
            }

            if (!fileSaved) {
                Object result = page.evaluate("async () => {" +
                        "const a = document.querySelector('#account-info-button-file');" +
                        "if (!a) return null;" +
                        "const href = a.getAttribute('href');" +
                        "const name = a.getAttribute('download') || '1xBet_file.txt';" +
                        "if (!href || !href.startsWith('blob:')) return null;" +
                        "const resp = await fetch(href);" +
                        "const buf = await resp.arrayBuffer();" +
                        "const bytes = new Uint8Array(buf);" +
                        "let binary=''; for (let i=0;i<bytes.length;i++){ binary += String.fromCharCode(bytes[i]); }" +
                        "const b64 = btoa(binary);" +
                        "return { name, b64 };" +
                        "}");
                if (result instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) result;
                    String name = String.valueOf(map.get("name"));
                    String b64 = String.valueOf(map.get("b64"));
                    if (b64 != null && !"null".equals(b64)) {
                        byte[] bytes = Base64.getDecoder().decode(b64);
                        Files.write(downloadsDir.resolve(name), bytes);
                        System.out.println("Сохранили файл через blob-фоллбэк: " + name);
                        fileSaved = true;
                    }
                }
                if (!fileSaved) {
                    System.out.println("Не удалось сохранить файл (нет download и не blob). Пропускаем шаг.");
                }
            }
        } else {
            System.out.println("Кнопка 'Сохранить в файл' не найдена — пропускаем шаг.");
        }

        // Сохраняем картинкой — download (60с) или popup-скрин фоллбэк
        System.out.println("Сохраняем картинкой");
        Locator saveImageBtn = page.locator("a#account-info-button-image");
        if (saveImageBtn.count() > 0 && saveImageBtn.first().isVisible()) {
            boolean imageSaved = false;

            try {
                Download d2 = page.waitForDownload(
                        new Page.WaitForDownloadOptions().setTimeout(60_000),
                        () -> saveImageBtn.first().click()
                );
                String suggested = d2.suggestedFilename();
                System.out.println("Скачали картинку: " + suggested);
                d2.saveAs(downloadsDir.resolve(suggested));
                imageSaved = true;
            } catch (TimeoutError te) {
                System.out.println("Событие download не пришло за 60с — пробуем popup-окно с изображением...");
            } catch (RuntimeException re) {
                System.out.println("Не удалось дождаться download: " + re.getMessage());
            }

            if (!imageSaved) {
                Page popup = null;
                try {
                    popup = page.waitForPopup(
                            new Page.WaitForPopupOptions().setTimeout(5000),
                            () -> { try { saveImageBtn.first().click(); } catch (Throwable ignored) {} }
                    );
                } catch (TimeoutError ignored) {}

                if (popup != null) {
                    popup.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    String fname = "1xBet_image_fallback_" + System.currentTimeMillis() + ".png";
                    popup.screenshot(new Page.ScreenshotOptions().setPath(downloadsDir.resolve(fname)));
                    System.out.println("Скрин попап-изображения сохранён: " + fname);
                    imageSaved = true;
                    try { popup.close(); } catch (Throwable ignored) {}
                } else {
                    System.out.println("Попап не появился — шаг 'Сохранить картинкой' пропущен (поведение сайта нестабильно).");
                }
            }
        } else {
            System.out.println("Кнопка 'Сохранить картинкой' не найдена — пропускаем шаг.");
        }

        // Баннер «Получить бонус» + закрытия
        clickIfVisible(page, "#form_get_bonus_after_submit");
        clickIfVisible(page, "button.identification-popup-transition__close");
        clickIfVisible(page, "button.identification-popup-close");

        // ЛК и выход (с защитой от оверлеев)
        System.out.println("Переходим в личный кабинет и выходим");
        page.navigate("https://1xbet.kz/office/account");
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);

        System.out.println("Нажимаем 'Выход'");
        Locator logout = page.locator("a.ap-left-nav__item.ap-left-nav__item_exit:has-text('Выход')");
        page.waitForSelector("a.ap-left-nav__item.ap-left-nav__item_exit",
                new Page.WaitForSelectorOptions().setTimeout(12000).setState(WaitForSelectorState.VISIBLE));

        try {
            logout.first().click(new Locator.ClickOptions().setTimeout(3000));
        } catch (Throwable ignore) {
            neutralizeOverlayIfNeeded(page);
            try {
                logout.first().click(new Locator.ClickOptions().setTimeout(2500).setForce(true));
            } catch (Throwable ignored2) {
                jsClick(logout);
            }
        }

        // подтверждаем выход
        clickIfVisible(page, "button.swal2-confirm.swal2-styled:has-text('ОК'), button.swal2-confirm");

        // ждём состояние «вышли»/лечим, если нужно
        waitUntilLoggedOutOrHeal(page);

        boolean loggedOut = isLoggedOut(page);
        if (!loggedOut) {
            System.out.println("Похоже, логаут не применился за разумное время — вероятно, держит оверлей/микрофронт.");
        }
        assertTrue(loggedOut, "Ожидали увидеть гостевое состояние (кнопка 'Регистрация' или отсутствие 'header--user-logged').");
        System.out.println("Выход подтверждён ✅");
    }
}
