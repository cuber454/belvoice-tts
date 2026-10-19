# BelVoice — беларускі сінтэзатар маўлення для Android / белорусский синтез речи

Офлайн-движок синтеза речи для Android на белорусском языке: ставится как обычный голос
в настройках телефона, после чего любая читалка и TalkBack читают белорусский текст.
Интернет не нужен: разрешения `INTERNET` в манифесте нет.

Это **форк [RuVoice TTS](https://github.com/kost-t-human/ruvoice-tts) 1.0.0** (автор —
kost-t-human). Движок, паузы, темп, профили, словари и доступность — его; переделаны
голос и текстовый слой. Оригинальное описание сохранено в [README.upstream.md](README.upstream.md).

## Что нового против исходного RuVoice

| Часть | Было (русский) | Стало (белорусский) |
|---|---|---|
| Модель синтеза | Silero `v5_5_ru` (CC BY-NC-SA) | Silero `v5_cis_base_nostress`, **MIT** |
| Голоса | 5 русских | **31**: `bel_larisa`, `bel_anatoliy`, `bel_dmitriy` + 28 дикторов той же модели |
| Ударения | Silero Stress (ru) + AOT + BERT-омографы | акцентор `accentor-bel.pt` (MIT) + свой словарь на 22 354 поправки |
| Числа | русский нормализатор (3616 строк) | свой `BelNormalizer` + `BelNumbers`: числа, годы, даты, время, единицы, валюты, сокращения |
| Латиница | транслит в русские буквы | свой `Latin`: словарь известных слов, диграфы, аббревиатуры по буквам |
| Интонация | входы модели «тип предложения» и «логическое ударение» | **контур высоты**: подъём для вопроса, выше и спадом для восклицания |
| Язык движка | `rus` | `bel`/`be`, локаль `be_BY`, голос по умолчанию `bel_larisa` |
| Пакет | `ru.kost.ruvoice` | `com.cuber.belvoice` — ставится рядом, не поверх |

## Как это устроено

Модель CIS содержит 60 дикторов в одних и тех же весах: голос — это 128 чисел, которые
подаются сети на вход (таблица дикторов занимает 60 КБ, сеть — 81 МБ). Поэтому добавление
голосов размер не меняет: сборка с 3 голосами и с 31 весит одинаково.

У модели нет входа «тип предложения» (11 аргументов forward против 13 у `v5_5_ru`), поэтому
вопрос и восклицание делаются не моделью, а контуром высоты по символам (`text/Intonation.kt`):
вопрос — подъём на последней трети фразы, восклицание — выше и спад в конце. Сила берётся
из настройки «сила логического ударения» (1–3), включение — галочка «Интонация по типу
предложения». Вопросы с вопросительным словом («Дзе ты быў?») не трогаются: там модель
сама падает в конце, как надо. Короткие фразы (меньше 8 символов) тоже не трогаются.

## Чего нет (и почему)

- **Логическое ударение `*слово*`** — это вход `focus_mask`, у CIS-модели его нет.
- **Твёрдое [э]**, словарь «ё» — русские данные, для белорусского не нужны или неприменимы.
- **Омографы** (замак/замок) — белорусского классификатора нет, различаются только словарём.
- **Эмодзи и служебные символы словами** — таблиц CLDR для `be` в сборке нет.
- **Интерфейс** пока на русском (строки авторские).
- «г» между гласными (`нага`, `магу`) и `чы` в `тысячы` голоса выговаривают по-русски —
  свойство записи дикторов Silero; записью слова это не лечится (проверяли на трёх голосах).

## Сборка

Нужно: JDK 17, Android SDK с платформой 35, NDK 30 (для стрипа `.so`), Python 3.13 с torch.
Linux-NDK r28c нужен только для AAR ExecuTorch.

```sh
# 1. модель Silero в tools/ (в git её нет)
curl -o tools/v5_cis_base_nostress.pt https://models.silero.ai/models/tts/ru/v5_cis_base_nostress.pt
# 2. пак голосов -> dist/ruvoice-pack-cis_be.zip (31 голос)
cd tools && python export_pack.py cis_be && cd ..
# 3. акцентор -> app/src/main/assets/silero/accentor.ptl
python tools/export_bel_accentor.py
# 4. данные модели -> app/src/main/assets/silero/silero_be.json
python tools/make_bel_json.py
# 5. модель в полную сборку -> app/src/full/assets/silero/
python tools/place_bel_model.py
# 6. рантайм вокодера: app/libs/executorch-1.5.0-xnnpack.aar
#    собирается tools/build_executorch_aar.sh; на Windows проверено в WSL:
#    cmake 3.31 (pip), Linux-NDK r28c, хост-компилятор C++ — zig из pip
# 7. APK
gradlew.bat :app:assembleFullDebug      # с моделью, 31 голос: 162 МБ
gradlew.bat :app:assembleLiteDebug      # без модели (80,7 МБ): голос ставится паком
```

Локальные сборки подписываются ключом из `local.properties` (`release.storeFile`,
`release.storePassword`, `release.keyAlias`, `release.keyPassword`), иначе — обычным
отладочным.

Проверка: `gradlew.bat :app:testFullDebugUnitTest` — 567 тестов.

## Файлы, добавленные в форк

```
app/src/main/java/com/cuber/belvoice/text/BelNormalizer.kt   текст → модель (числа, знаки, фильтр)
app/src/main/java/com/cuber/belvoice/text/BelNumbers.kt      даты, время, единицы, валюты, сокращения
app/src/main/java/com/cuber/belvoice/text/BelStress.kt       ударения: акцентор + правила
app/src/main/java/com/cuber/belvoice/text/Latin.kt           латиница → белорусские буквы
app/src/main/java/com/cuber/belvoice/text/Intonation.kt      контуры вопроса и восклицания
app/src/main/java/com/cuber/belvoice/text/TextPrep.kt        выбор текстового слоя по языку
app/src/main/assets/dicts/stress/Системный.txt               22 354 поправки к акцентору (из bel.dic)
tools/export_bel_accentor.py                                 accentor-bel.pt → accentor.ptl
tools/make_bel_json.py                                       данные модели (символы, голоса, вопросы)
tools/place_bel_model.py                                     модель из пака в assets
tools/import_bel_dict.py                                     разбор словаря ударений, сверка с акцентором
tools/latin_probe.py, g_probe.py, intonation_probe.py, numbered_probe.py, say_words.py, ru_voice_probe.py
                                                             пробы на слух (латиница, «г», интонация, голоса)
```

## Лицензии

- Silero `v5_cis_base_nostress` — **MIT** (`LICENSE_CIS` в репозитории Silero).
- silero-stress `accentor-bel.pt` — **MIT**.
- Код RuVoice — файла LICENSE в репозитории автора нет; форк разрешён его публичным
  сообщением в Telegram-канале. Перед публичным релизом нужно личное подтверждение
  или файл LICENSE.
- Sonic (темп) — Apache-2.0, ExecuTorch — BSD-3, PyTorch Android Lite и данные CLDR —
  по лицензиям правообладателей.
