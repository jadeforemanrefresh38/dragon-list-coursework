# Источники реализации

Использованы исходники и инструкции официальных проектов GitHub. Собственный фильтр на Python, ищущий слова `SELECT` или `script`, не используется: основной контроль находится в Java, а дополнительную проверку HTTP выполняет Coraza с OWASP CRS.

| Проект | Что использовано |
| --- | --- |
| [Spring Security](https://github.com/spring-projects/spring-security) | Аутентификация, BCrypt, CSRF, защита сессий и заголовки; версия из BOM Spring Boot 3.5.16 |
| [Coraza CRS Docker](https://github.com/coreruleset/coraza-crs-docker) | Официальный образ Caddy с модулем Coraza; README, Dockerfile и шаблоны конфигурации |
| [Coraza Caddy](https://github.com/corazawaf/coraza-caddy) | `load_owasp_crs` и включение встроенных правил |
| [OWASP Core Rule Set](https://github.com/coreruleset/coreruleset) | Проверки SQLi, XSS, обхода путей, команд и протокола; загруженная версия CRS 4.25.0 |
| [PostgreSQL Docker](https://github.com/docker-library/postgres) | Образ PostgreSQL 16 Alpine и начальная настройка ролей |

Образы, использованные 22.10.2026, закреплены в Dockerfile/Compose:

```text
eclipse-temurin@sha256:b3054a20e1a851574cdb1692dd0db5310c647e62214a2a7abecbec1b9e3ae8f1
postgres@sha256:deb28ee471183cb0c303113dac9b2433c51eeeeb678def7af9a9ec5820367815
ghcr.io/coreruleset/coraza-crs@sha256:627229dd936934d773cf1a8de77ec9ae89d547b1a891e6da3b46b0282bb694cc
```

При изучении Coraza CRS Docker прочитан README с Git blob SHA `b575d735f03795ba6af840c162e26f70994ec6de`. Это идентификатор прочитанного файла, не SHA коммита релиза. Воспроизводимость запуска задаётся digest образа и нашей конфигурацией в репозитории.

В производном образе WAF бинарный файл копируется без capability привязки к привилегированным портам, а процесс запускается от UID 1000 на 8080. Генерирующий конфигурацию upstream-entrypoint заменён фиксированной конфигурацией, что позволяет использовать корень только для чтения. Код Coraza и CRS не переписывался; условия лицензий компонентов сохраняются в их исходниках и образах.

Из правил исключены только свободные JSON-поля заметки: SQL/XSS-сигнатуры для `json.title` и `json.content`, RCE/LFI-сигнатуры для `json.content`. Исключение ограничено маршрутами создания и редактирования заметки; остальные параметры продолжают проверяться. Защита БД от SQLi обеспечивается параметрами и правами роли независимо от WAF.

Окончание обычной поддержки Debian 11 подтверждено [официальным сообщением Debian от 31.08.2026](https://www.debian.org/News/2026/20260831). Контейнерное развёртывание совместимо с хостом, но не продлевает поддержку его ядра.
