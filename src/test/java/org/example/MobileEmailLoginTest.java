package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MobileEmailLoginTest {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(List.of("--start-maximized")) // открыть окно в полный размер
        );
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(null) // без ограничения viewport
        );
        page = context.newPage();
    }

    // Браузер остаётся открытым
    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void loginWithEmailAndSms() {
        System.out.println("Открываем мобильную версию сайта 1xbet.kz");
        page.navigate("https://1xbet.kz/?platform_type=mobile");

        System.out.println("Жмём 'Войти' в шапке");
        page.waitForTimeout(1000);
        page.click("button#curLoginForm >> text=Войти");

        // --- Заменили ID на Email ---
        System.out.println("Вводим Email");
        page.waitForTimeout(1000);
        page.fill("input#auth_id_email", "mynameisjante@gmail.com");

        System.out.println("Вводим пароль");
        page.waitForTimeout(1000);
        page.fill("input#auth-form-password", "Aezakmi11+");

        System.out.println("Жмём кнопку 'Войти'");
        page.waitForTimeout(1000);
        page.click("button.auth-button:has(span.auth-button__text:has-text('Войти'))");

        System.out.println("Ждём 20 секунд (решение капчи вручную)");
        page.waitForTimeout(20000);

        // --- Отправляем код подтверждения ---
        System.out.println("Жмём 'Выслать код'");
        page.waitForTimeout(1000);
        page.click("button.phone-sms-modal-content__send");

        System.out.println("Ждём 20 секунд (капча после отправки SMS, если есть)");
        page.waitForTimeout(20000);

        // --- Google Messages ---
        System.out.println("Открываем Google Messages");
        Page messagesPage = context.newPage();
        messagesPage.navigate("https://messages.google.com/web/conversations");

        System.out.println("Закрываем уведомление 'Нет, не нужно' (если есть)");
        if (messagesPage.locator("button:has-text('Нет, не нужно')").isVisible()) {
            messagesPage.waitForTimeout(1000);
            messagesPage.click("button:has-text('Нет, не нужно')");
        }

        System.out.println("Жмём кнопку 'Подключить, отсканировав QR-код'");
        messagesPage.waitForTimeout(1000);
        messagesPage.locator("span.qr-text:has-text('Подключить, отсканировав QR-код')").click();

        System.out.println("Ищем последнее сообщение от 1xBet");
        Locator lastMessage = messagesPage.locator("mws-conversation-list-item").first();
        lastMessage.waitFor();

        String smsText = lastMessage.innerText();
        System.out.println("Содержимое SMS: " + smsText);

        // Код может содержать и буквы, и цифры (6–8 символов)
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

        // --- Вставляем код на сайте ---
        page.bringToFront();
        System.out.println("Вводим код подтверждения");
        page.waitForTimeout(1000);
        page.fill("input.phone-sms-modal-code__input", code);

        System.out.println("Жмём 'Подтвердить'");
        page.waitForTimeout(1000);
        page.click("button.phone-sms-modal-content__send:has-text('Подтвердить')");

        // --- Заходим в Личный кабинет ---
        System.out.println("Открываем 'Личный кабинет'");
        page.waitForTimeout(2000);
        page.click("button.user-header__link.header__link.header__reg");

        System.out.println("Жмём 'Выход'");
        page.waitForTimeout(1000);
        page.click("button.drop-menu-list__link_exit");

        System.out.println("Подтверждаем выход кнопкой 'Ок'");
        page.waitForTimeout(1000);
        page.click("button.swal2-confirm.swal2-styled");

        System.out.println("Выход завершён ✅ (браузер остаётся открытым)");
    }
}
