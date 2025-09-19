package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;

public class BetTest {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(Arrays.asList("--start-maximized"))); // теперь List<String>
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(null));
        page = context.newPage();
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void loginWithSmsAndBet() {
        System.out.println("Открываем сайт 1xbet.kz");
        page.navigate("https://1xbet.kz/");

        System.out.println("Жмём 'Войти' в шапке");
        page.waitForTimeout(1000);
        page.click("button#login-form-call");
        page.waitForTimeout(1000);

        System.out.println("Вводим ID");
        page.fill("input#auth_id_email", "168715375");
        page.waitForTimeout(1000);

        System.out.println("Вводим пароль");
        page.fill("input#auth-form-password", "Aezakmi11+");
        page.waitForTimeout(1000);

        System.out.println("Жмём 'Войти' в форме авторизации");
        page.click("button.auth-button.auth-button--block.auth-button--theme-secondary");
        page.waitForTimeout(1000);

        System.out.println("Ждём 20 секунд (капча после 'Войти', если есть)");
        page.waitForTimeout(20000);

        System.out.println("Ждём модальное окно SMS");
        page.waitForSelector("button:has-text('Выслать код')");

        System.out.println("Жмём 'Выслать код'");
        page.click("button:has-text('Выслать код')");
        page.waitForTimeout(1000);

        System.out.println("Ждём 25 секунд (капча после отправки SMS, если есть)");
        page.waitForTimeout(25000);

        System.out.println("Открываем Google Messages");
        Page messagesPage = context.newPage();
        messagesPage.navigate("https://messages.google.com/web/conversations");

        System.out.println("Закрываем уведомление 'Нет, не нужно' (если есть)");
        if (messagesPage.locator("button:has-text('Нет, не нужно')").isVisible()) {
            messagesPage.click("button:has-text('Нет, не нужно')");
            page.waitForTimeout(1000);
        }

        System.out.println("Жмём кнопку 'Подключить, отсканировав QR-код'");
        messagesPage.locator("span.qr-text:has-text('Подключить, отсканировав QR-код')").click();
        page.waitForTimeout(1000);

        System.out.println("Ищем последнее сообщение от 1xBet");
        Locator lastMessage = messagesPage.locator("mws-conversation-list-item").first();
        lastMessage.waitFor();

        String smsText = lastMessage.innerText();
        System.out.println("Содержимое SMS: " + smsText);

        Pattern pattern = Pattern.compile("\\b[a-zA-Z0-9]{6,8}\\b");
        Matcher matcher = pattern.matcher(smsText);
        String code = null;
        if (matcher.find()) {
            code = matcher.group();
        }

        if (code == null) {
            throw new RuntimeException("Не удалось извлечь код подтверждения из SMS: " + smsText);
        }

        System.out.println("Извлечённый код подтверждения: " + code);

        System.out.println("Возвращаемся на сайт 1xbet.kz");
        page.bringToFront();

        System.out.println("Вводим код подтверждения");
        page.fill("input.phone-sms-modal-code__input", code);
        page.waitForTimeout(1000);

        System.out.println("Жмём 'Подтвердить'");
        page.click("button:has-text('Подтвердить')");
        page.waitForTimeout(1000);

        System.out.println("Авторизация завершена ✅");

        // --- Совершение ставки ---
        System.out.println("Выбираем первый доступный исход");
        Locator firstOutcome = page.locator("span.c-bets__inner").first();
        firstOutcome.click();
        page.waitForTimeout(1000);

        System.out.println("Вводим сумму ставки: 50");
        page.fill("input.cpn-value-controls__input", "50");
        page.keyboard().press("Enter");
        page.waitForTimeout(1000);

        System.out.println("Пробуем нажать 'Сделать ставку'");
        String makeBetBtn = "button.cpn-btn.cpn-btn--theme-accent:has-text('Сделать ставку')";
        page.waitForSelector(makeBetBtn + ":not([disabled])");
        try {
            page.click(makeBetBtn);
            System.out.println("Кнопка 'Сделать ставку' нажата ✅");
        } catch (Exception e) {
            page.evaluate("document.querySelector(\"button.cpn-btn.cpn-btn--theme-accent\").click()");
            System.out.println("JS-клик по кнопке 'Сделать ставку'");
        }
        page.waitForTimeout(1000);

        System.out.println("Жмём 'Блокировать' во всплывающем окне");
        String blockBtn = "a.pf-subs-btn-link__secondary";
        page.waitForSelector(blockBtn);
        page.click(blockBtn);
        page.waitForTimeout(1000);

        System.out.println("Жмём 'Ok' в подтверждении");
        String okBtn = "button.c-btn.c-btn--size-m.c-btn--block.c-btn--gradient.c-btn--gradient-accent";
        page.waitForSelector(okBtn);
        try {
            page.click(okBtn);
            System.out.println("Кнопка 'Ok' нажата ✅");
        } catch (Exception e) {
            page.evaluate("document.querySelector(\"button.c-btn.c-btn--size-m.c-btn--block.c-btn--gradient.c-btn--gradient-accent\").click()");
            System.out.println("JS-клик по кнопке 'Ok'");
        }
        page.waitForTimeout(1000);

        // --- Проверка истории ---
        System.out.println("Открываем 'Личный кабинет'");
        page.click("a.header-lk-box-link[title='Личный кабинет']");
        page.waitForTimeout(1000);

        System.out.println("Открываем 'История ставок'");
        page.click("div.ap-left-nav__item_history");
        page.waitForTimeout(1000);

        System.out.println("Разворачиваем первую ставку");
        page.click("button.apm-panel-head__expand");
        page.waitForTimeout(1000);

        // --- Выход ---
        System.out.println("Выходим из аккаунта");
        page.click("a.ap-left-nav__item_exit");
        page.waitForTimeout(1000);

        System.out.println("Подтверждаем выход 'ОК'");
        page.click("button.swal2-confirm.swal2-styled");
        page.waitForTimeout(1000);

        System.out.println("Выход завершён ✅ (браузер остаётся открытым)");
    }
}
