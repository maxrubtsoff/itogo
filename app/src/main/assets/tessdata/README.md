# OCR-модель Tesseract (кириллица)

В этой папке лежит модель русского языка для распознавания кэшбэка с фото:

    app/src/main/assets/tessdata/rus.traineddata   (~19 МБ, tessdata 4.0.0)

Источник: пакет npm `@tesseract.js-data/rus` (официальные tessdata
проекта tesseract-ocr), версия 4.0.0 — совместима с Tesseract 4
(tesseract4android 4.7.0).

## Как используется
При первом запуске `TesseractOcrEngine` копирует файл из assets в
`filesDir/tess/tessdata/` (Tesseract читает модель с диска). Если файл
убрать — приложение не падает: распознавание отключается
(`isReady()=false`), скрин получает статус «Нужны вы», кэшбэк вводится
вручную.

## Замена на более точную модель (по желанию)
Для лучшего качества есть `rus.traineddata` из `tessdata_best`
(медленнее, крупнее). Положить сюда под тем же именем.
