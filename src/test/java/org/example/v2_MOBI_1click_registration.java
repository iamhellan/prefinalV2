package org.example;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.util.Random;

public class v2_MOBI_1click_registration {
    static Playwright playwright;
    static Browser browser;
    static BrowserContext context;
    static Page page;

    @BeforeAll
    static void setUpAll() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)
                .setArgs(java.util.List.of("--start-maximized")));
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(null));
        page = context.newPage();
    }

    @AfterAll
    static void tearDownAll() {
        System.out.println("[REG-1CLICK] Тест завершён ✅ (браузер остаётся открытым)");
    }

    @Test
    void registrationOneClick() {
        // Открываем сайт
        System.out.println("[REG-1CLICK] Открываем сайт 1xbet.kz (mobile)");
        page.navigate("https://1xbet.kz/?platform_type=mobile");

        // Жмём "Регистрация"
        System.out.println("[REG-1CLICK] Жмём 'Регистрация'");
        page.locator("button[aria-label='Регистрация']").click();

        // Выбираем вкладку "В 1 клик"
        System.out.println("[REG-1CLICK] Выбираем вкладку 'В 1 клик'");
        page.locator("button.c-registration__tab:has-text('В 1 клик')").click();

        // Вводим рандомный промокод
        Random random = new Random();
        String promo = String.valueOf(10000000 + random.nextInt(90000000));
        System.out.println("[REG-1CLICK] Вводим рандомный промокод: " + promo);
        page.fill("input#registration_ref_code", promo);

        // Работа с бонусами
        System.out.println("[REG-1CLICK] Открываем бонусы через дефолтное значение 'Получать бонусы'");
        page.locator("span.c-registration__text:has-text('Получать бонусы')").click();

        System.out.println("[REG-1CLICK] Выбираем 'Отказ от бонусов'");
        page.locator("span.c-registration__text:has-text('Отказ от бонусов')").click();

        System.out.println("[REG-1CLICK] Повторно выбираем 'Отказ от бонусов'");
        page.locator("span.c-registration__text:has-text('Отказ от бонусов')").click();

        System.out.println("[REG-1CLICK] Возвращаемся и выбираем 'Получать бонусы'");
        page.locator("span.c-registration__text:has-text('Получать бонусы')").click();

        // Завершаем регистрацию
        System.out.println("[REG-1CLICK] Жмём 'Зарегистрироваться'");
        page.locator("div.submit_registration").click();
    }
}
