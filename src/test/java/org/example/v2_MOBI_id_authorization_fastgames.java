package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_MOBI_id_authorization_fastgames {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(List.of("--start-maximized"))
        );
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(null)
        );
        page = context.newPage();
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    // ===== УТИЛИТЫ ============================================================

    private Frame findFrameWithSelector(Page p, String selector, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Page pg : p.context().pages()) {
                for (Frame f : pg.frames()) {
                    try {
                        if (f.locator(selector).count() > 0) {
                            System.out.println("[DEBUG] Нашли селектор в фрейме: " + f.url());
                            return f;
                        }
                    } catch (Throwable ignore) {}
                }
            }
            p.waitForTimeout(300);
        }
        return null;
    }

    private Locator smartLocator(Page p, String selector, int timeoutMs) {
        Locator direct = p.locator(selector);
        if (direct.count() > 0) return direct;

        Frame f = findFrameWithSelector(p, selector, timeoutMs);
        if (f != null) return f.locator(selector);

        throw new RuntimeException("Элемент не найден ни на странице, ни в её фреймах: " + selector);
    }

    private void robustClick(Page p, Locator loc, int timeoutMs, String debugName) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        RuntimeException lastErr = null;

        while (System.currentTimeMillis() < deadline) {
            try {
                loc.first().scrollIntoViewIfNeeded();
                loc.first().click(new Locator.ClickOptions().setTimeout(3000));
                return;
            } catch (RuntimeException e1) {
                lastErr = e1;
                String msg = e1.getMessage() == null ? "" : e1.getMessage();
                boolean intercept = msg.contains("intercepts pointer events");
                try { loc.first().evaluate("el => el.scrollIntoView({block:'center', inline:'center'})"); } catch (Throwable ignore) {}

                if (intercept) {
                    try {
                        loc.first().click(new Locator.ClickOptions().setTimeout(2500).setForce(true));
                        return;
                    } catch (RuntimeException e2) {
                        lastErr = e2;
                        try {
                            loc.first().evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true}))");
                            return;
                        } catch (RuntimeException e3) { lastErr = e3; }
                    }
                } else {
                    try {
                        loc.first().click(new Locator.ClickOptions().setTimeout(2500).setForce(true));
                        return;
                    } catch (RuntimeException e2) {
                        lastErr = e2;
                        try {
                            loc.first().evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true}))");
                            return;
                        } catch (RuntimeException e3) { lastErr = e3; }
                    }
                }
            }
            p.waitForTimeout(200);
        }
        if (lastErr != null) throw lastErr;
        throw new RuntimeException("Не удалось кликнуть по '" + debugName + "' за " + timeoutMs + "ms");
    }

    private void restorePointerEvents(Page p, Locator sameDocElement) {
        try {
            p.evaluate("() => { const h = document.querySelector('header.header'); if (h && h.dataset._pe !== undefined) { h.style.pointerEvents = h.dataset._pe; delete h.dataset._pe; } }");
        } catch (Throwable ignore) {}
        try {
            sameDocElement.first().evaluate("el => { const doc = el.ownerDocument; const bar = doc.querySelector('.bet-bar-desktop'); if (bar && bar.dataset._pe !== undefined) { bar.style.pointerEvents = bar.dataset._pe; delete bar.dataset._pe; } }");
        } catch (Throwable ignore) {}
    }

    private void clickFirstEnabled(Page p, String selector, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            Locator group;
            try {
                group = smartLocator(p, selector, 1500);
            } catch (RuntimeException e) {
                p.waitForTimeout(200);
                continue;
            }

            int count = group.count();
            for (int i = 0; i < count; i++) {
                Locator candidate = group.nth(i);

                boolean visible;
                try { visible = candidate.isVisible(); } catch (Throwable t) { visible = false; }
                if (!visible) continue;

                boolean enabled;
                try {
                    enabled = (Boolean) candidate.evaluate("e => !(e.classList && e.classList.contains('pointer-events-none'))");
                } catch (Throwable t) {
                    enabled = true;
                }

                if (enabled) {
                    robustClick(p, candidate, 8000, selector + " [nth=" + i + "]");
                    return;
                }
            }
            p.waitForTimeout(200);
        }
        throw new RuntimeException("Не дождались активного элемента по селектору: " + selector);
    }

    private void clickFirstEnabledAny(Page p, String[] selectors, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String lastTried = "";
        while (System.currentTimeMillis() < deadline) {
            for (String sel : selectors) {
                lastTried = sel;
                try {
                    clickFirstEnabled(p, sel, 1200);
                    return;
                } catch (RuntimeException ignore) {}
            }
            p.waitForTimeout(150);
        }
        throw new RuntimeException("Не нашли активный элемент ни по одному из селекторов (последний пробовали): " + lastTried);
    }

    private Page clickCardMaybeOpensNewTab(Locator card) {
        int before = context.pages().size();
        robustClick(page, card, 30000, "game-card");
        page.waitForTimeout(600);
        int after = context.pages().size();
        if (after > before) {
            Page newPage = context.pages().get(after - 1);
            newPage.bringToFront();
            System.out.println("[DEBUG] Игра открылась в новой вкладке: " + newPage.url());
            return newPage;
        }
        System.out.println("[DEBUG] Игра открылась в текущем окне/фрейме");
        return page;
    }

    private void passTutorialIfPresent(Page gamePage) {
        for (int i = 1; i <= 5; i++) {
            try {
                Locator nextBtn = smartLocator(gamePage, "div[role='button']:has-text('Далее')", 600);
                if (nextBtn.count() == 0 || !nextBtn.first().isVisible()) break;
                System.out.println("Нажимаем 'Далее' (" + i + ")");
                robustClick(gamePage, nextBtn.first(), 2000, "Далее");
                gamePage.waitForTimeout(150);
            } catch (RuntimeException ignore) { break; }
        }
        try {
            Locator understood = smartLocator(gamePage, "div[role='button']:has-text('Я все понял'), div[role='button']:has-text('Я всё понял')", 600);
            if (understood.count() > 0 && understood.first().isVisible()) {
                System.out.println("Нажимаем 'Я всё понял'");
                robustClick(gamePage, understood.first(), 2000, "Я всё понял");
            }
        } catch (RuntimeException ignore) {}
        gamePage.waitForTimeout(100);
    }

    private void setStake50ViaChip(Page gamePage) {
        System.out.println("Выбираем сумму ставки 50 (чип)");
        Locator chip50 = smartLocator(gamePage, "div.chip-text:has-text('50')", 2000);
        robustClick(gamePage, chip50.first(), 12000, "chip-50");
        gamePage.waitForTimeout(150);
    }

    private void waitRoundToSettle(Page gamePage, int maxMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxMs) {
            Locator anyBet = gamePage.locator("div[role='button'][data-market][data-outcome]:has-text('Сделать ставку')");
            try {
                if (anyBet.count() > 0 && anyBet.first().isVisible()) {
                    boolean enabled = (Boolean) anyBet.first().evaluate("e => !(e.classList && e.classList.contains('pointer-events-none'))");
                    if (enabled) {
                        System.out.println("[DEBUG] Новый раунд: «Сделать ставку» активны — можно переключаться");
                        return;
                    }
                }
            } catch (Throwable ignore) {}
            gamePage.waitForTimeout(150);
        }
        System.out.println("[WARN] По таймауту не увидели нового раунда");
    }

    private Page openGameByHrefContains(Page originPage, String hrefContains, String fallbackMenuText) {
        Frame f = findFrameWithSelector(originPage, "a[href*='" + hrefContains + "']", 5000);
        if (f == null && fallbackMenuText != null) {
            f = findFrameWithSelector(originPage, "span.text-hub-header-game-title:has-text('" + fallbackMenuText + "')", 5000);
        }
        if (f == null) {
            for (Frame fx : originPage.frames()) {
                if (fx.locator("a[href*='" + hrefContains + "']").count() > 0) { f = fx; break; }
            }
        }
        if (f == null) throw new RuntimeException("Не нашли ссылку на игру: " + hrefContains);

        Locator link = f.locator("a[href*='" + hrefContains + "']");
        if (link.count() == 0 && fallbackMenuText != null) {
            link = f.locator("span.text-hub-header-game-title:has-text('" + fallbackMenuText + "')").locator("xpath=ancestor::a");
        }
        if (link.count() == 0) throw new RuntimeException("Ссылка на игру не найдена в фрейме: " + hrefContains);

        link.first().scrollIntoViewIfNeeded();
        return clickCardMaybeOpensNewTab(link.first());
    }

    private boolean waitAndSellAny(Page gamePage, int preDelayMs, int totalTimeoutMs) {
        gamePage.waitForTimeout(preDelayMs);
        long deadline = System.currentTimeMillis() + totalTimeoutMs;

        while (System.currentTimeMillis() < deadline) {
            try {
                Locator anyBet = gamePage.locator("div[role='button'][data-market][data-outcome]:has-text('Сделать ставку')");
                if (anyBet.count() > 0 && anyBet.first().isVisible()) {
                    boolean enabled = (Boolean) anyBet.first().evaluate("e => !(e.classList && e.classList.contains('pointer-events-none'))");
                    if (enabled) {
                        System.out.println("[INFO] Новый раунд до продажи — пропускаем продажу");
                        return false;
                    }
                }
            } catch (Throwable ignore) {}

            try {
                Locator pause = gamePage.locator(":text('Пауза')");
                if (pause.count() > 0 && pause.first().isVisible()) {
                    System.out.println("[INFO] Пауза — ждём снятия…");
                    gamePage.waitForTimeout(500);
                    continue;
                }
            } catch (Throwable ignore) {}

            Locator sells = gamePage.locator("div[role='button'][data-market='hit_met_condition']:has-text('Продать'), div[role='button'][data-market='hit_met_condition']:has-text('Вывести')");
            int n = sells.count();
            for (int i = 0; i < n; i++) {
                Locator btn = sells.nth(i);
                boolean visible = false, enabled = true;
                try { visible = btn.isVisible(); } catch (Throwable ignore) {}
                try { enabled = (Boolean) btn.evaluate("e => !(e.classList && e.classList.contains('pointer-events-none'))"); } catch (Throwable ignore) {}
                if (visible && enabled) {
                    System.out.println("[DEBUG] Нашли кнопку продажи — кликаем");
                    robustClick(gamePage, btn, 6000, "Продать/Вывести");
                    return true;
                }
            }
            gamePage.waitForTimeout(150);
        }
        return false;
    }

    private Page openUniqueBoxingFromHub(Page originPage) {
        String innerSpan = "a.menu-sports-item-inner[href*='productId=boxing'][href*='cid=1xbetkz'] " +
                "span.text-hub-header-game-title:has-text('Бокс')";
        Frame f = findFrameWithSelector(originPage, innerSpan, 8000);
        if (f == null) {
            for (Frame fx : originPage.frames()) {
                if (fx.locator(innerSpan).count() > 0) { f = fx; break; }
            }
        }
        if (f == null) throw new RuntimeException("Не нашли уникальную кнопку 'Бокс' по заданному селектору ❌");

        Locator span = f.locator(innerSpan);
        if (span.count() == 0 || !span.first().isVisible())
            throw new RuntimeException("Кнопка 'Бокс' не видна ❌");

        Locator link = span.first().locator("xpath=ancestor::a");
        link.first().scrollIntoViewIfNeeded();
        return clickCardMaybeOpensNewTab(link.first());
    }

    // ===== ТЕСТ ===============================================================

    @Test
    void loginAndPlayFastGames() {
        // === Авторизация ===
        System.out.println("Открываем мобильную версию 1xbet.kz");
        page.navigate("https://1xbet.kz/?platform_type=mobile");

        System.out.println("Жмём 'Войти'");
        page.waitForTimeout(1000);
        page.click("button#curLoginForm >> text=Войти");

        System.out.println("Вводим ID");
        page.waitForTimeout(1000);
        page.fill("input#auth_id_email", "168715375");

        System.out.println("Вводим пароль");
        page.waitForTimeout(1000);
        page.fill("input#auth-form-password", "Aezakmi11+");

        System.out.println("Жмём 'Войти'");
        page.waitForTimeout(1000);
        page.click("button.auth-button:has(span.auth-button__text:has-text('Войти'))");

        System.out.println("Ждём 20 сек для капчи");
        page.waitForTimeout(20000);

        System.out.println("Жмём 'Выслать код'");
        page.waitForTimeout(800);
        page.click("button.phone-sms-modal-content__send");

        System.out.println("Ждём 20 сек для капчи после SMS (если есть)");
        page.waitForTimeout(20000);

        // --- Google Messages (с открытием QR) ---
        System.out.println("Открываем Google Messages");
        Page messagesPage = context.newPage();
        messagesPage.navigate("https://messages.google.com/web/conversations");

        System.out.println("Закрываем 'Нет, не нужно' (если есть)");
        Locator noThanks = messagesPage.locator("button:has-text('Нет, не нужно')");
        if (noThanks.isVisible()) {
            messagesPage.waitForTimeout(500);
            noThanks.click();
        }

        System.out.println("Жмём 'Подключить, отсканировав QR-код' (если не подключено)");
        Locator qrBtn = messagesPage.locator("span.qr-text:has-text('Подключить, отсканировав QR-код'), text=Подключить, отсканировав QR-код");
        if (qrBtn.count() > 0 && qrBtn.first().isVisible()) {
            qrBtn.first().click();
            System.out.println("[INFO] Открыл окно с QR — отсканируй его телефоном, затем обнови список сообщений.");
        }

        System.out.println("Ищем последнее сообщение от 1xBet");
        Locator lastMessage = messagesPage.locator("mws-conversation-list-item").first();
        lastMessage.waitFor();

        String smsText = lastMessage.innerText();
        System.out.println("Содержимое SMS: " + smsText);

        Pattern pattern = Pattern.compile("\\b[a-zA-Z0-9]{6,8}\\b");
        Matcher matcher = pattern.matcher(smsText);
        String code = matcher.find() ? matcher.group() : null;
        if (code == null) throw new RuntimeException("Не удалось извлечь код подтверждения из SMS: " + smsText);
        System.out.println("Извлечённый код подтверждения: " + code);

        // --- Вставляем код на сайте ---
        page.bringToFront();
        System.out.println("Вводим код подтверждения");
        page.waitForTimeout(1000);
        page.fill("input.phone-sms-modal-code__input", code);

        System.out.println("Жмём 'Подтвердить'");
        page.waitForTimeout(800);
        page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");

        page.waitForTimeout(3000);
        System.out.println("Авторизация завершена ✅");

        // === Быстрые игры (мобильная навигация к хабу) ===
        System.out.println("Открываем меню (гамбургер)");
        page.click("button.header__hamburger.hamburger");
        page.waitForTimeout(1200);

        System.out.println("Переходим в 'Быстрые игры'");
        page.click("a.drop-menu-list__link[href*='fast-games']");
        page.waitForTimeout(2500);

        // ===== Crash Boxing =====
        System.out.println("Открываем 'Crash Boxing'");
        Locator crashTile = page.locator("div.tile__cell img[alt='Crash boxing']").first();
        Page gamePage = clickCardMaybeOpensNewTab(crashTile);
        gamePage.waitForTimeout(800);
        passTutorialIfPresent(gamePage);

        System.out.println("Уменьшаем сумму до 'Мин'");
        clickFirstEnabled(gamePage, "span[role='button']:has-text('Мин')", 20000);
        gamePage.waitForTimeout(200);

        System.out.println("Ставка 50 KZT (yes)");
        clickFirstEnabled(gamePage, "div[role='button'][data-market='hit_met_condition'][data-outcome='yes']:has-text('Сделать ставку')", 30000);

        System.out.println("Ставка 50 KZT (yes_2)");
        clickFirstEnabled(gamePage, "div[role='button'][data-market='hit_met_condition'][data-outcome='yes_2']:has-text('Сделать ставку')", 30000);

        waitRoundToSettle(gamePage, 25000);

        // ===== Нарды =====
        System.out.println("Переходим в игру 'Нарды'");
        Page nardsPage = openGameByHrefContains(gamePage, "nard", "Нарды");
        nardsPage.waitForTimeout(600);
        passTutorialIfPresent(nardsPage);
        setStake50ViaChip(nardsPage);
        System.out.println("Выбираем исход: Синий");
        clickFirstEnabled(nardsPage, "span[role='button'][data-market='dice'][data-outcome='blue']", 20000);
        waitRoundToSettle(nardsPage, 25000);

        // ===== Дартс =====
        System.out.println("Переходим в игру 'Дартс'");
        Page dartsPage = openGameByHrefContains(nardsPage, "darts?cid", "Дартс");
        dartsPage.waitForTimeout(600);
        passTutorialIfPresent(dartsPage);
        setStake50ViaChip(dartsPage);
        System.out.println("Выбираем исход (1-4-5-6-9-11-15-16-17-19)");
        clickFirstEnabled(dartsPage, "span[role='button'][data-market='1-4-5-6-9-11-15-16-17-19']", 20000);
        waitRoundToSettle(dartsPage, 25000);

        // ===== Дартс - Фортуна =====
        System.out.println("Переходим в игру 'Дартс - Фортуна'");
        Page dartsFortunePage = openGameByHrefContains(dartsPage, "darts-fortune", "Дартс - Фортуна");
        dartsFortunePage.waitForTimeout(600);
        passTutorialIfPresent(dartsFortunePage);
        setStake50ViaChip(dartsFortunePage);
        System.out.println("Выбираем исход: ONE_TO_EIGHT (Сектор 1-8)");
        clickFirstEnabled(dartsFortunePage, "div[data-outcome='ONE_TO_EIGHT']", 20000);
        waitRoundToSettle(dartsFortunePage, 25000);

        // ===== Больше/Меньше =====
        System.out.println("Переходим в игру 'Больше/Меньше'");
        Page hiloPage = openGameByHrefContains(dartsFortunePage, "darts-hilo", "Больше/Меньше");
        hiloPage.waitForTimeout(600);
        passTutorialIfPresent(hiloPage);
        setStake50ViaChip(hiloPage);
        System.out.println("Выбираем исход: Больше или равно (>=16)");
        clickFirstEnabledAny(hiloPage, new String[]{
                "div[role='button'][data-market='THROW_RESULT'][data-outcome='gte-16']",
                "div.board-market-hi-eq:has-text('Больше или равно')"
        }, 45000);
        waitRoundToSettle(hiloPage, 30000);

        // ===== Буллиты NHL21 =====
        System.out.println("Переходим в игру 'Буллиты NHL21'");
        Page shootoutPage = openGameByHrefContains(hiloPage, "shootout", "Буллиты NHL21");
        shootoutPage.waitForTimeout(800);
        passTutorialIfPresent(shootoutPage);
        System.out.println("Выбираем чип 50");
        setStake50ViaChip(shootoutPage);
        System.out.println("Выбираем исход: Да");
        clickFirstEnabled(shootoutPage, "div[role='button'].market-button:has-text('Да')", 45000);
        waitRoundToSettle(shootoutPage, 35000);

        // ===== Бокс (уникальная кнопка) =====
        System.out.println("Переходим в игру 'Бокс' (уникальная кнопка)");
        Page boxingPage = openUniqueBoxingFromHub(shootoutPage);
        boxingPage.waitForTimeout(600);
        passTutorialIfPresent(boxingPage);
        setStake50ViaChip(boxingPage);
        System.out.println("Выбираем исход: боксёр №1 (первая кнопка)");
        clickFirstEnabled(boxingPage, "div[role='button'].contest-panel-outcome-button", 20000);
        waitRoundToSettle(boxingPage, 20000);

        System.out.println("Готово ✅");
    }
}
