# Изоляция фильтрации UI и экрана загрузки

## Воспроизведённая причина

`RenderSystem.setShaderTexture(0, id)` выбирает будущий sampler шейдера, но для уже загруженной картинки не вызывает немедленный `glBindTexture`. Следующий `glTexParameteri` изменял MIN/MAG-фильтры **предыдущей** привязанной текстуры. Это были восемь draw-методов: Image, два метода AlphaComponent, два метода SaturationComponent, горизонтальные hue-полосы двух color-picker окон и крест конфигурации.

Изменение сохраняется в самом объекте текстуры. Восстановление GL bindings не восстанавливает фильтры. Более того, Minecraft кеширует фильтр framebuffer/AbstractTexture: повторный `setTexFilter(NEAREST)` с тем же значением может вообще не вызвать GL и не исправить чужое изменение.

До исправления настоящий клиентский GL-тест воспроизвёл смену фильтра постороннего HUD framebuffer на первом прогретом `Image` draw: `Image raster fallback preserves cached HUD filters with active unit 0` завершился ошибкой. Это не только предположение по исходникам.

## Исправление

- Из draw-методов удалены все ambient-вызовы изменения фильтров. У Image также удалён лишний предварительный выбор shader texture перед MSDF/raster веткой.
- Собственные raster-иконки и градиенты получают линейную фильтрацию через `.png.mcmeta` при загрузке. Поле `texture.blur: true` означает bilinear sampling этой конкретной картинки, а не постобработку мира/HUD. Метаданные добавлены и к module-иконкам для качественного raster fallback при недоступном MSDF atlas.
- Minecraft block/item atlas, vanilla font atlases, mipmaps и HUD framebuffer policy не переопределяются. MSDF и blur framebuffer по-прежнему используют свои адресные object API.
- LoadingFont теперь выделяет/освобождает atlas через `TextureUtil`. Удаление инвалидирует GlStateManager cache на всех units: сырой `glDeleteTextures` мог оставлять устаревший GL name, а повторное использование этого имени — пропускать необходимый bind.
- Загрузочный экран сохраняет прежнюю изоляцию на unit 7, явный bind собственного atlas перед upload/filter и восстановление GL state.

## Производительность и качество

Нет новых проходов, framebuffer, texture reads, проверок GL или «возврата NEAREST» каждый кадр. Из обычного UI draw удалены два GL-вызова фильтрации; метаданные обрабатываются только при загрузке/reload. Дополнительная работа TextureUtil выполняется только при создании/освобождении экрана загрузки. Частота обновления HUD/blur, сила blur и сохранение позиций не менялись. Это структурное отсутствие новой покадровой нагрузки, не обещание одинакового FPS во всех окружениях.

## Проверки

```powershell
.\gradlew.bat verifyTextureFiltering
.\gradlew.bat runTextureFilterSmoke -PtextureFilterOnly
.\gradlew.bat runHudBlurSmoke -PhudBlurOnly
.\gradlew.bat build
```

- Offline regression запрещает texture mutations и дополнительные GL queries в исправленных UI-классах; проверяет упакованные метаданные и cache-aware lifecycle загрузочных текстур.
- GL regression прогревает все девять UI draw-вариантов и проверяет неизменность постороннего HUD sampler с NEAREST / NEAREST_MIPMAP_LINEAR при активных units 0, 1, 2, 7; проверяет настоящий block/item atlas.
- Проверяется отсутствие рассинхронизации hardware binding / GlStateManager cache, cold/warm bootstrap draw, повторный close/reinitialize и удаление atlas, намеренно привязанного одновременно на двух units.
- Весь набор повторяется после настоящего Minecraft resource reload. Отдельный LoadingOverlayClientTest проверяет нулевой прогресс, округлённую полосу, success/failure/replacement lifecycle и screenshots; он ждёт новые кадры перед чтением pixels, а не предыдущий кадр меню.
- Graphical fixtures находятся только в `build/texture-filter-client-test` и `build/hud-blur-client-test`; пользовательские миры/конфиги не используются.

## Результаты после исправления

- `verifyTextureFiltering`: 418 offline-проверок успешно.
- `runTextureFilterSmoke -PtextureFilterOnly`: 3 627 GL-проверок успешно, включая настоящий resource reload; screenshots и lifecycle загрузочного экрана, а также HUD/2D render safety прошли.
- `runHudBlurSmoke -PhudBlurOnly`: 7 328 591 GL-проверка успешно, максимальная разница цветового канала с эталоном — 1/255. Группировка, половинные кеши, перемещение HUD и плавность переходов blur не регрессировали в проверенных сценариях.
- Полный `build --console=plain` без фильтрующих test properties завершился успешно, включая `remapJar`, 832 032 offline-проверки оптимизаций blur и 88 проверок сохранения конфигов/HUD. Ошибки записи/чтения внутри `verifyConfigPersistence` — намеренная проверка retry и восстановления из backup, а не сбой сборки.
- Готовый runtime JAR проверен отдельно: все 101 UI `.mcmeta` и девять blur shader-ресурсов совпадают с текущими исходниками; исправленные UI-классы не содержат texture-filter вызовов или GL queries, bootstrap использует cache-aware TextureUtil lifecycle и явную привязку собственного atlas. Классы blur и восстановления позиций присутствуют, test fixtures и переопределение vanilla block-atlas metadata отсутствуют.

Сборка: `build/libs/PhazeClient-1.2.jar` (26 036 777 байт).

SHA-256: `74790D51AD99A186386B8781DF25B9FD2E15D202E2A53684ADC4F6A8D66B70D6`.

После замены JAR нужен полный перезапуск игры: уже повреждённые texture objects старого процесса автоматически не исправляются. Отдельные окружения Sodium/Iris/Exordium и длительные игровые FPS-прогоны в рамках этой проверки не подтверждены.
