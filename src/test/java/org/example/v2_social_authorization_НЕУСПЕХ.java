package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;

public class v2_social_authorization_НЕУСПЕХ {
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
                        .setArgs(Arrays.asList("--start-maximized")) // окно на весь экран
        );
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(null)); // отключаем фиксированный viewport
        page = context.newPage();
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void loginWithGoogleAndSms() {
        System.out.println("Открываем сайт 1xbet.kz");
        page.navigate("https://1xbet.kz/");

        System.out.println("Жмём 'Войти'");
        page.click("button#login-form-call");

        System.out.println("Жмём кнопку Google");
        page.click("a.auth-social__link--google");

        // ждём попап Google
        Page popup = page.waitForPopup(() -> System.out.println("Ожидание окна Google..."));
        popup.waitForLoadState();

        System.out.println("Вводим email");
        popup.fill("input[type='email']", "mynameisjante@gmail.com");
        popup.click("button:has-text('Далее')");
        popup.waitForTimeout(2000);

        System.out.println("Вводим пароль");
        popup.fill("input[type='password']", "Hesoyam11+");
        popup.click("button:has-text('Далее')");
        popup.waitForTimeout(4000);

        System.out.println("Скроллим страницу вниз и жмём 'Продолжить'");
        popup.waitForClose(() -> {
            popup.evaluate("window.scrollTo(0, document.body.scrollHeight)");
            popup.click("button:has-text('Продолжить')");
        });
        System.out.println("Google-попап закрылся ✅");

        // выводим все открытые вкладки для отладки
        for (Page p : context.pages()) {
            System.out.println("Активная вкладка: " + p.url());
        }

        // ждём появления кнопки в SMS-модалке
        page.locator("button.phone-sms-modal-content__send")
                .waitFor(new Locator.WaitForOptions().setTimeout(30000));
        System.out.println("Нашли кнопку 'Выслать код' ✅");

        // кликаем по кнопке
        page.click("button.phone-sms-modal-content__send");
        System.out.println("Жмём 'Выслать код'");
        page.waitForTimeout(20000);

        // --- Google Messages ---
        System.out.println("Открываем Google Messages");
        Page messagesPage = context.newPage();
        messagesPage.navigate("https://messages.google.com/web/conversations");

        if (messagesPage.locator("button:has-text('Нет, не нужно')").isVisible()) {
            messagesPage.click("button:has-text('Нет, не нужно')");
        }

        Locator lastMessage = messagesPage.locator("mws-conversation-list-item").first();
        lastMessage.waitFor();
        String smsText = lastMessage.innerText();
        System.out.println("Содержимое SMS: " + smsText);

        Pattern pattern = Pattern.compile("\\b[a-zA-Z0-9]{6,8}\\b");
        Matcher matcher = pattern.matcher(smsText);
        String code = matcher.find() ? matcher.group() : null;
        if (code == null) throw new RuntimeException("Не удалось извлечь код: " + smsText);

        System.out.println("Извлечённый код: " + code);

        page.bringToFront();
        page.fill("input.phone-sms-modal-code__input", code);
        page.click("button:has-text('Подтвердить')");
        page.waitForTimeout(2000);

        // --- Выход ---
        page.click("a.header-lk-box-link[title='Личный кабинет']");
        page.click("a.ap-left-nav__item_exit");
        page.click("button.swal2-confirm.swal2-styled");

        System.out.println("✅ Авторизация через Google + SMS успешно завершена!");
    }
}
