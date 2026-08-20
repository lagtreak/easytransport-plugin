# EasyTransport

EasyTransport - плагин для Рескублики, добавляющий транспортную систему с билетерами, остановками, заявками игроков и Discord Webhook.

---

## Как работает поездка

Игрок подходит к билетеру - выбирает область - выбирает город - видит время и стоимость - подтверждает поездку - телепортируется в промежуточную точку - ждёт истечение таймера - прибывает в пункт назначения.

Стоимость списывается после прибытия. Каждые 5 секунд во время поездки проверяется наличие средств. При нехватке денег игрок возвращается в исходную точку.

Для поездки между мирами используется базовое время и базовая цена мира назначения плюс расстояние от его координат `0,0`.

---

## Транспорт

| Транспорт | Скорость      | Цена за 200 блоков |
| --------- | -------------:| ------------------:|
| Аўтобус   | 202.5 блок./с | 1 BYN              |
| Цягнік    | 337.5 блок./с | 3 BYN              |
| Самалёт   | 675 блок./с   | 6 BYN              |

<img title="" src="file:///E:/easytransport/raw/a6e403cb5c4c1b2a.png" alt="" data-align="center">

Скорость и цена настраиваются командами.

---

## Области

* Брэсцкая
* Віцебская
* Гомельская
* Гродзенская
* Магілёўская
* Мінская
* Замежжа

В GUI области отображаются цветными блоками, города - цветными кожаными ботинками.

<img title="" src="file:///E:/easytransport/raw/Снимок экрана 2026-08-20 172243.png" alt="Снимок экрана 2026-08-20 172243.png" width="395" data-align="center">

<img title="" src="file:///E:/easytransport/raw/Снимок экрана 2026-08-20 172426.png" alt="Снимок экрана 2026-08-20 172426.png" width="401" data-align="center">

---

## Билетеры

* Jungle - Аўтобус
* Savanna - Цягнік
* Snow - Самалёт

Все билетеры имеют профессию рыбака, имя `Білетар` и направление, заданное администратором при создании.

<img title="" src="file:///E:/easytransport/raw/2026-08-20_17.15.06.png" alt="2026-08-20_17.15.06.png" width="538" data-align="center">

---

## Основные команды администратора

Все административные команды доступны OP.

### Билетеры

```text
/etr cashier create bus|train|air
/etr cashier delete bus|train|air
/etr cashier deleteall
```

### Остановки

```text
/etr stop create bus|train|air <Вобласць> <Назва>
/etr stop delete bus|train|air <Вобласць> <Назва>
/etr stop deleteall
```

### Промежуточные точки

```text
/etr betweentp create bus|train|air
/etr betweentp delete bus|train|air
```

### Цена и скорость

```text
/etr coast bus|train|air <Цена>
/etr coast abroadbase <Цена>
/etr speed bus|train|air <Скорость>
```

## Настраиваемые миры

EasyTransport может обслуживать любые существующие миры сервера.

```text
/etr world add <Мир>
/etr world delete <Мир>
/etr world list
/etr world info <Мир>
/etr world name <Мир> <Название>
/etr world transport <Мир> bus|train|air on|off
/etr world baseprice <Мир> <Цена>
/etr world basetime <Мир> <Секунды>
```

При добавлении плагин проверяет, существует ли такой мир на сервере.

### Базовые миры

Роли `Беларускі край` и `Замежжа` можно привязать к другим физическим мирам:

```text
/etr world bind belarus <Мир>
/etr world bind abroad <Мир>
```

При переназначении старый базовый мир удаляется из реестра EasyTransport, а его настройки переносятся на новый.

## Заявки игроков

Игрок может предложить свою остановку:

```text
/etr request create bus|train|air <Вобласць> <Назва>
```

Администратор открывает заявки:

```text
/etr application
```

<img title="" src="file:///C:/Users/angam/AppData/Roaming/marktext/images/2026-08-20-17-31-19-image.png" alt="" data-align="center" width="432">
Для каждой заявки доступны:

* одобрение;
* отказ с причиной;
* телепортация к точке.

<img title="" src="file:///E:/easytransport/raw/Снимок экрана 2026-08-20 173332.png" alt="Снимок экрана 2026-08-20 173332.png" data-align="center" width="390">

Для одобрения в радиусе 50 блоков должен находиться билетер нужного транспорта. Это сделано, для того, чтобы администратор на забывал, из-за великого удобства одобрения, добавлять билетера. Для отказа билетер не требуется.

После одобрения заявка превращается в обычную остановку.

## Discord Webhook

```text
/etr discord webhook <URL>
/etr discord status
/etr discord test
/etr discord sync
/etr discord off
```

<img title="" src="file:///C:/Users/angam/AppData/Roaming/marktext/images/2026-08-20-17-38-44-image.png" alt="" data-align="center" width="352">
Webhook синхронизирует активные заявки с Discord после создания, одобрения, отказа и перезапуска сервера.

## Частицы

```text
/etr particles
```

По умолчанию частицы включены, по ним видно в какую область отправляется игрок. Частицы представляют из себя нимб цвета области точки назначения.
