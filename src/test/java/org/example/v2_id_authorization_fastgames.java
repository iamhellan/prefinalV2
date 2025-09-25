package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.jupiter.api.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class v2_id_authorization_fastgames {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    // ===== УТИЛИТЫ ============================================================

    private com.microsoft.playwright.Frame findFrameWithSelector(Page p, String selector, int timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            for (Page pg : p.context().pages()) {
                for (com.microsoft.playwright.Frame f : pg.frames()) {
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

        com.microsoft.playwright.Frame f = findFrameWithSelector(p, selector, timeoutMs);
        if (f != null) return f.locator(selector);

        throw new RuntimeException("Элемент не найден ни на странице, ни в её фреймах: " + selector);
    }

    /** Устойчивый клик + обход перекрытий (header/bet-bar). */
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
                    System.out.println("[INFO] '" + debugName + "': перехват указателя. Временно отключим overlays и повторим.");
                    try {
                        p.evaluate("() => { const h = document.querySelector('header.header'); if (h) { h.dataset._pe = h.style.pointerEvents || ''; h.style.pointerEvents = 'none'; } }");
                    } catch (Throwable ignore) {}
                    try {
                        loc.first().evaluate("el => { const doc = el.ownerDocument; const bar = doc.querySelector('.bet-bar-desktop'); if (bar) { bar.dataset._pe = bar.style.pointerEvents || ''; bar.style.pointerEvents = 'none'; } }");
                    } catch (Throwable ignore) {}

                    // force
                    try {
                        loc.first().click(new Locator.ClickOptions().setTimeout(2500).setForce(true));
                        restorePointerEvents(p, loc);
                        return;
                    } catch (RuntimeException e2) {
                        lastErr = e2;
                        // JS-клик
                        try {
                            loc.first().evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true}))");
                            restorePointerEvents(p, loc);
                            return;
                        } catch (RuntimeException e3) {
                            lastErr = e3;
                            restorePointerEvents(p, loc);
                        }
                    }
                } else {
                    // без intercept — force → JS
                    try {
                        loc.first().click(new Locator.ClickOptions().setTimeout(2500).setForce(true));
                        return;
                    } catch (RuntimeException e2) {
                        lastErr = e2;
                        try {
                            loc.first().evaluate("el => el.dispatchEvent(new MouseEvent('click', {bubbles:true, cancelable:true}))");
                            return;
                        } catch (RuntimeException e3) {
                            lastErr = e3;
                        }
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

    /** как только снова доступны «Сделать ставку», считаем, что новый раунд */
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
        com.microsoft.playwright.Frame f = findFrameWithSelector(originPage, "a[href*='" + hrefContains + "']", 5000);
        if (f == null && fallbackMenuText != null) {
            f = findFrameWithSelector(originPage, "span.text-hub-header-game-title:has-text('" + fallbackMenuText + "')", 5000);
        }
        if (f == null) {
            for (com.microsoft.playwright.Frame fx : originPage.frames()) {
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

    private void setStake50ViaChip(Page gamePage) {
        System.out.println("Выбираем сумму ставки 50 (чип)");
        Locator chip50 = smartLocator(gamePage, "div.chip-text:has-text('50')", 2000);
        robustClick(gamePage, chip50.first(), 12000, "chip-50");
        gamePage.waitForTimeout(150);
    }

    /** Ищем любой активный «Продать»/«Вывести» в hit_met_condition, с учётом паузы. (оставлено на будущее, сейчас не используется в Крэш-бокс) */
    private boolean waitAndSellAny(Page gamePage, int preDelayMs, int totalTimeoutMs) {
        gamePage.waitForTimeout(preDelayMs);
        long deadline = System.currentTimeMillis() + totalTimeoutMs;

        while (System.currentTimeMillis() < deadline) {
            // если начался новый раунд – выходим
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

            // Пауза?
            try {
                Locator pause = gamePage.locator(":text('Пауза')");
                if (pause.count() > 0 && pause.first().isVisible()) {
                    System.out.println("[INFO] Пауза — ждём снятия, но не дольше таймаута…");
                    gamePage.waitForTimeout(500);
                    continue; // просто пережидаем
                }
            } catch (Throwable ignore) {}

            // Любая кнопка «Продать»/«Вывести» в hit_met_condition
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

    /** УНИКАЛЬНОЕ открытие «Бокс» по твоему HTML-селектору */
    private Page openUniqueBoxingFromHub(Page originPage) {
        String innerSpan = "a.menu-sports-item-inner[href*='productId=boxing'][href*='cid=1xbetkz'] " +
                "span.text-hub-header-game-title:has-text('Бокс')";

        com.microsoft.playwright.Frame f = findFrameWithSelector(originPage, innerSpan, 8000);
        if (f == null) {
            // fallback: вдруг рендерится в основном документе/других фреймах
            for (com.microsoft.playwright.Frame fx : originPage.frames()) {
                if (fx.locator(innerSpan).count() > 0) { f = fx; break; }
            }
        }
        if (f == null) throw new RuntimeException("Не нашли уникальную кнопку 'Бокс' по заданному селектору ❌");

        Locator span = f.locator(innerSpan);
        if (span.count() == 0 || !span.first().isVisible())
            throw new RuntimeException("Кнопка 'Бокс' не видна ❌");

        // Кликаем по <a>, к которой относится span
        Locator link = span.first().locator("xpath=ancestor::a");
        link.first().scrollIntoViewIfNeeded();
        return clickCardMaybeOpensNewTab(link.first());
    }

    // ===== ТЕСТ ===============================================================
    @Test
    void loginAndPlayFastGames() {
        System.out.println("Открываем сайт 1xbet.kz");
        page.navigate("https://1xbet.kz/");

        System.out.println("Жмём 'Войти' в шапке");
        page.waitForTimeout(800);
        page.click("button#login-form-call");

        System.out.println("Вводим ID");
        page.fill("input#auth_id_email", "168715375");

        System.out.println("Вводим пароль");
        page.fill("input#auth-form-password", "Aezakmi11+");

        System.out.println("Жмём 'Войти' в форме авторизации");
        page.waitForTimeout(500);
        page.click("button.auth-button.auth-button--block.auth-button--theme-secondary");

        // >>>> Ждём, пока появится «Выслать код» (первая капча решена)
        System.out.println("Ждём, пока появится кнопка \"Выслать код\" (решаю капчу вручную, если есть)");
        page.waitForSelector("button:has-text('Выслать код')",
                new Page.WaitForSelectorOptions().setTimeout(120000).setState(WaitForSelectorState.VISIBLE));

        System.out.println("Ждём модальное окно SMS");
        page.waitForSelector("button:has-text('Выслать код')");

        System.out.println("Жмём 'Выслать код'");
        page.waitForTimeout(700);
        page.click("button:has-text('Выслать код')");

        // >>>> Ждём поле ввода кода (вторая капча решена)
        System.out.println("Ждём появление поля ввода кода (решаю вторую капчу вручную, если есть)");
        page.waitForSelector("input.phone-sms-modal-code__input",
                new Page.WaitForSelectorOptions().setTimeout(120000).setState(WaitForSelectorState.VISIBLE));

        // Google Messages (скан QR вручную)
        System.out.println("Открываем Google Messages");
        Page messagesPage = context.newPage();
        messagesPage.navigate("https://messages.google.com/web/conversations");

        System.out.println("Жмём кнопку 'Подключить, отсканировав QR-код'");
        messagesPage.waitForTimeout(800);
        messagesPage.locator("span.qr-text:has-text('Подключить, отсканировав QR-код')").click();

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

        System.out.println("Возвращаемся на сайт 1xbet.kz");
        page.bringToFront();

        System.out.println("Вводим код подтверждения");
        page.waitForTimeout(500);
        page.fill("input.phone-sms-modal-code__input", code);

        System.out.println("Жмём 'Подтвердить'");
        page.waitForTimeout(500);
        page.click("button:has-text('Подтвердить')");

        System.out.println("Авторизация завершена ✅");

        // ===== Быстрые игры → Крэш-Бокс =====
        System.out.println("Переходим в 'Быстрые игры'");
        page.waitForTimeout(1200);
        page.click("a.header-menu-nav-list-item__link.main-item:has-text('Быстрые игры')");

        System.out.println("Ищем карточку 'Крэш-Бокс' (через href) в фреймах");
        com.microsoft.playwright.Frame gamesFrame = findFrameWithSelector(page, "a.game[href*='crash-boxing']", 8000);
        if (gamesFrame == null) {
            gamesFrame = findFrameWithSelector(page, "p.game-name:has-text('Крэш-Бокс')", 12000);
        }
        if (gamesFrame == null) {
            for (com.microsoft.playwright.Frame fx : page.frames()) {
                if (fx.locator("a.game[href*='crash-boxing']").count() > 0) { gamesFrame = fx; break; }
            }
        }
        if (gamesFrame == null) {
            List<com.microsoft.playwright.Frame> frames = page.frames();
            System.out.println("[DEBUG] Фреймы на странице:");
            for (com.microsoft.playwright.Frame f : frames) System.out.println(" - " + f.url());
            throw new RuntimeException("Не удалось найти карточку 'Крэш-Бокс' ни в одном iframe ❌");
        }

        Locator crashByHref = gamesFrame.locator("a.game[href*='crash-boxing']");
        Locator crashByText = gamesFrame.locator("p.game-name:has-text('Крэш-Бокс')").locator("xpath=ancestor::a");
        Locator crashCard = crashByHref.count() > 0 ? crashByHref : crashByText;

        System.out.println("Ждём появления карточки в DOM");
        crashCard.waitFor(new Locator.WaitForOptions().setTimeout(20000).setState(WaitForSelectorState.ATTACHED));

        System.out.println("Кликаем по Крэш-Бокс");
        Page gamePage = clickCardMaybeOpensNewTab(crashCard);
        gamePage.waitForTimeout(800);

        passTutorialIfPresent(gamePage);

        System.out.println("Уменьшаем сумму до 'Мин'");
        clickFirstEnabled(gamePage, "span[role='button']:has-text('Мин')", 20000);
        gamePage.waitForTimeout(200);

        System.out.println("Ставка 50 KZT (yes)");
        clickFirstEnabled(gamePage, "div[role='button'][data-market='hit_met_condition'][data-outcome='yes']:has-text('Сделать ставку')", 30000);

        System.out.println("Ставка 50 KZT (yes_2)");
        clickFirstEnabled(gamePage, "div[role='button'][data-market='hit_met_condition'][data-outcome='yes_2']:has-text('Сделать ставку')", 30000);

        // Продажу убрали. Как только «Сделать ставку» снова активны — сразу уходим в следующую игру.
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
        System.out.println("Ждём появления суммы (чип 50)");
        setStake50ViaChip(shootoutPage);
        System.out.println("Выбираем исход: Да");
        clickFirstEnabled(shootoutPage, "div[role='button'].market-button:has-text('Да')", 45000);
        waitRoundToSettle(shootoutPage, 35000);

        // ===== Бокс (уникальная кнопка из твоего HTML) =====
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
