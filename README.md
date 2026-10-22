# Dragon list

Приложение на Java для личных заметок с одной из четырёх меток: Важное, Идеи, Учеба, Личное. Пять страниц: регистрация, вход, список, заметка, добавление/редактирование. Интерфейс рассчитан на компьютер.

## Стек

Java 17, Maven 3.6.3+, Spring Boot 3.5.16, Spring MVC, Thymeleaf, Spring Security, Bean Validation, Spring Data JPA, Flyway. Версии библиотек согласованы BOM Spring Boot. PostgreSQL для VPS, H2 для запуска без установки БД. Контейнеры и Node.js для работы приложения не нужны.

## Локальный запуск

Установите JDK 17 и Maven. Проверьте `java -version` и `mvn -version`.

```sh
mvn clean verify
java -jar target/dragon-list-1.0.0.jar
```

Откройте http://127.0.0.1:8080 и зарегистрируйтесь. Данные сохраняются в `data/`; демонстрационных аккаунтов нет. H2 работает в файловом режиме. После перезапуска заметки сохраняются.

## Debian 11 и PostgreSQL

Используется JDK/JRE 17 (Java 11 недостаточно). Приложение собирается с целевой версией Java 17. На VPS достаточно JRE 17 и PostgreSQL 13 из Debian 11. Сборку можно выполнить на другом компьютере.

```sh
sudo apt update
sudo apt install openjdk-17-jre-headless postgresql nginx
sudo -u postgres createuser --pwprompt dragonlist
sudo -u postgres createdb --owner=dragonlist dragonlist
sudo useradd --system --home /opt/dragon-list --shell /usr/sbin/nologin dragonlist
sudo install -d -o root -g dragonlist -m 750 /opt/dragon-list
sudo install -o root -g dragonlist -m 640 target/dragon-list-1.0.0.jar /opt/dragon-list/dragon-list.jar
sudo install -o root -g root -m 600 deploy/dragon-list.env.example /etc/dragon-list.env
sudo nano /etc/dragon-list.env
```

Впишите пароль PostgreSQL в `DB_PASSWORD`, оставьте профиль `prod`. Приложение подключается к PostgreSQL на localhost. Flyway создаёт таблицы, Hibernate проверяет схему. Пароль нельзя добавлять в репозиторий.

```sh
sudo cp deploy/dragon-list.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now dragon-list
sudo journalctl -u dragon-list -n 50 --no-pager
```

Скопируйте `deploy/nginx.conf` в `/etc/nginx/sites-available/dragon-list`, замените `notes.example.com` на свой домен, включите конфигурацию ссылкой в `sites-enabled`. Проверьте `sudo nginx -t` и перезагрузите nginx. Настройте DNS и TLS-сертификат для домена, например через Certbot. В профиле `prod` cookie передаётся только по HTTPS: сначала настройте TLS, затем входите через домен. Порт 8080 слушает только localhost; извне должны быть доступны 80/443 и ваш порт SSH. Заголовки прокси доверяются при таком размещении за локальным nginx.

Резервная копия: `sudo -u postgres pg_dump -Fc dragonlist > dragonlist.dump`. Восстановление в пустую БД: `sudo -u postgres pg_restore --no-owner --role=dragonlist -d dragonlist dragonlist.dump`. Храните копии отдельно от VPS и проверяйте восстановление. Для обновления остановите сервис, замените jar, запустите сервис; перед миграциями сохраните копию БД.

## Настройки

`DB_URL`, `DB_USER`, `DB_PASSWORD` — подключение к БД. `PORT` — порт (8080), `SERVER_ADDRESS` — адрес (127.0.0.1). Необязательный `DONATION_URL` принимает HTTPS-ссылку: при его заполнении появляется «Поддержать проект». `COOKIE_SECURE` предназначен для локального профиля; prod всегда требует HTTPS.

## Устройство и безопасность

Контроллер принимает формы, сервис проверяет владельца и выполняет изменения в транзакциях, репозитории работают с БД. Запросы к заметкам всегда учитывают логин из серверной сессии. Чужая и отсутствующая заметка возвращают 404. Передача user_id через форму не используется. Пароли хешируются BCrypt; логины приводятся к нижнему регистру. Уникальность логина обеспечивается БД. Изменения выполняются POST-запросами с CSRF-токеном. Thymeleaf экранирует текст заметок. Лимиты: заголовок 200, текст 20000 символов, логин 3–32, пароль 8–64 печатных ASCII-символа (чтобы не превысить ограничение BCrypt в байтах).

`users`: id, login, password (хеш). `notes`: id, user_id, title, content, tag, created_at. Внешний ключ связывает заметки с владельцем. CHECK ограничивает метки. Индексы ускоряют выборку заметок пользователя с фильтром и сортировкой. Время хранится как Instant, в интерфейсе показано по Москве.

Маршруты: `GET/POST /register`, `GET/POST /login`, `POST /logout`, `GET /notes?tag=STUDY`, `GET /notes/new`, `POST /notes`, `GET /notes/{id}`, `GET/POST /notes/{id}/edit`, `POST /notes/{id}/delete`. Это HTML-приложение с серверными формами, отдельного JSON API нет.

## Проверка

`mvn verify` запускает интеграционные проверки регистрации, входа, хеширования, валидации, CSRF, экранирования HTML, полного CRUD, фильтра и запрета доступа к чужим заметкам. Тестовая БД H2 создаётся миграцией Flyway. GitHub Actions повторяет сборку на JDK 17.

Ограничения первой версии: список без пагинации, нет восстановления пароля, корзины и ограничения частоты входа. Развёртывание на реальном VPS требует доступа к серверу и домена. Автоматические тесты на H2 не заменяют проверку PostgreSQL на сервере.

Дракончики перенесены из предыдущего проекта пользователя. Исходный файл задания сохранён отдельно и не изменялся.
