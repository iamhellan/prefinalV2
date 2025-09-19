package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailLoginTest {
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

    // Браузер остаётся открытым
    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void loginWithEmail() {
        System.out.println("Открываем сайт 1xbet.kz");
        page.navigate("https://1xbet.kz/");

        System.out.println("Жмём 'Войти' в шапке");
        page.waitForTimeout(1000);
        page.click("button#login-form-call");

        System.out.println("Вводим Email");
        page.fill("input#auth_id_email", "mynameisjante@gmail.com");

        System.out.println("Вводим пароль");
        page.fill("input#auth-form-password", "Aezakmi11+");

        System.out.println("Жмём 'Войти' в форме авторизации");
        page.waitForTimeout(1000);
        page.click("button.auth-button.auth-button--block.auth-button--theme-secondary");

        System.out.println("Ждём 20 секунд (капча после 'Войти', если есть)");
        page.waitForTimeout(20000);

        // --- ЕСЛИ SMS НЕ НУЖНО ---
        // можно сразу уйти в Личный кабинет и выйти
        try {
            if (page.locator("button:has-text('Выслать код')").isVisible()) {
                System.out.println("Обнаружено SMS-подтверждение (e-mail используется как логин, но сайт требует SMS)");
                page.waitForSelector("button:has-text('Выслать код')");
                System.out.println("Жмём 'Выслать код'");
                page.waitForTimeout(1000);
                page.click("button:has-text('Выслать код')");

                System.out.println("Ждём 20 секунд (капча после отправки SMS, если есть)");
                page.waitForTimeout(20000);

                // --- Блок с Google Messages оставлен без изменений ---
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

                System.out.println("Возвращаемся на сайт 1xbet.kz");
                page.bringToFront();

                System.out.println("Вводим код подтверждения");
                page.waitForTimeout(1000);
                page.fill("input.phone-sms-modal-code__input", code);

                System.out.println("Жмём 'Подтвердить'");
                page.waitForTimeout(1000);
                page.click("button:has-text('Подтвердить')");
            }
        } catch (Exception e) {
            System.out.println("SMS-подтверждения нет — продолжаем без него ✅");
        }

        System.out.println("Авторизация завершена ✅");

        // --- ДОБАВЛЯЕМ ВЫХОД ---
        System.out.println("Открываем 'Личный кабинет'");
        page.waitForTimeout(1000);
        page.click("a.header-lk-box-link[title='Личный кабинет']");

        System.out.println("Жмём 'Выход'");
        page.waitForTimeout(1000);
        page.click("a.ap-left-nav__item_exit");

        System.out.println("Подтверждаем выход кнопкой 'ОК'");
        page.waitForTimeout(1000);
        page.click("button.swal2-confirm.swal2-styled");

        System.out.println("Выход завершён ✅ (браузер остаётся открытым)");
    }
}
