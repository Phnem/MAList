# Безопасные источники потокового видео для MOVIE/SERIES

Дата проверки: 2026-08-10.

## Рамки исследования

Цель — источники, которые законно отдают непосредственно воспроизводимый поток или файл для контента пользователя либо для контента с явно разрешающей лицензией. Не рассматриваются embed-агрегаторы, пиратские балансеры, извлечение скрытых потоков, обход DRM/Cloudflare, кража токенов и каталоги, которые возвращают только ссылку «где смотреть».

Важное различие:

- наличие `m3u8`, `mpd` или `mp4` в ответе само по себе не даёт права использовать его вне официального плеера;
- для скачивания нужно отдельное разрешение API/владельца, а не только техническая возможность сделать HTTP GET;
- персональные серверы должны использоваться только с медиатекой, на которую у пользователя есть права.

## Краткий вывод

| Источник | Нативное воспроизведение | Скачивание | Приоритет |
|---|---|---|---|
| Прямой HTTPS URL / S3 presigned URL | Progressive, HLS, DASH | Да, если URL выдан владельцем для GET | 1 |
| WebDAV / Nextcloud | Progressive; HLS/DASH, если доступны все связанные файлы | Да, если сервер разрешает GET/download | 1 |
| Jellyfin | Direct stream или серверный HLS-транскод | Да, только при `canDownload`/разрешении пользователя | 2 |
| Emby | Direct stream или серверный транскод | Да, через официальный `/Items/{Id}/Download` при разрешении | 3 |
| Internet Archive | Обычно прямые MP4/WebM-файлы | Да, только для проверенного public-domain/CC/разрешённого материала | 4 |
| Wikimedia Commons | Прямые WebM/Ogg и транскоды | Да, с сохранением лицензии/атрибуции | 4 |
| Plex Personal Media Server | Да, но интеграция сложнее и более проприетарна | Только через разрешённый Plex Downloads workflow | 5 |
| VK Видео | API-схема может вернуть MP4 | Не документировано как разрешённый offline-download | Не включать в общий movie-search; только узкий opt-in |
| RUTUBE | Официально разрешён embed-плеер | Нет публичного разрешённого download API | Не считать нативным resolver без партнёрского договора |

## Совместимость с текущим Vetro

В проекте уже подключены Media3 ExoPlayer, HLS, DASH и `media3-datasource-okhttp`. `StreamingPlaybackSessionFactory` создаёт header-aware `OkHttpDataSource` из `VetroVideo.headers`, а `VetroVideo` уже представляет HTTP(S)-поток. Поэтому progressive/HLS/DASH-адаптеры не требуют нового плеера:

- `app/src/main/java/com/example/myapplication/media/player/StreamingPlaybackSession.kt`;
- `app/src/main/java/com/example/myapplication/media/source/VetroModels.kt`;
- `gradle/libs.versions.toml`.

Media3 официально поддерживает progressive-контейнеры, HLS и DASH; поддержка конкретных кодеков зависит от Android-устройства. [Android Media3: supported formats](https://developer.android.com/media/media3/exoplayer/supported-formats)

Текущий загрузчик Vetro уже умеет progressive HTTP с возобновлением через `Range` и HLS с последующим remux в MP4. DASH-offline пока не реализован: `.mpd` попадает в ветку adaptive, но загрузчик принимает только `.m3u8`. Media3 предоставляет штатные `DownloadService`, `DownloadManager` и `DownloadHelper`, включая adaptive media; это возможный будущий путь для DASH. [Android Media3: downloading media](https://developer.android.com/media/media3/exoplayer/downloading-media)

## 1. Прямой HTTPS URL и S3-compatible object storage

### Контракт

Пользователь явно добавляет URL одного из типов:

- progressive: MP4, WebM, MKV/другой поддерживаемый контейнер;
- HLS: master/media playlist `.m3u8`;
- DASH: manifest `.mpd`;
- S3-compatible presigned GET URL.

Для progressive-файла полезно проверить `Content-Type`, `Content-Length`, `ETag` и byte ranges. HTTP `Accept-Ranges: bytes` объявляет поддержку частичных запросов; клиент может послать `Range` и без предварительного `Accept-Ranges`. [RFC 9110, Range Requests](https://datatracker.ietf.org/doc/html/rfc9110#section-14)

S3 presigned URL даёт ограниченный по времени доступ к конкретной операции объекта. Для скачивания это подписанный `GET`; полномочия и срок URL наследуются от IAM-принципала, который его создал. [AWS S3: presigned URLs](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html)

### Auth/config

- URL без auth; либо пользовательские HTTP-заголовки (`Authorization`, cookie, custom header);
- presigned query parameters для S3-compatible storage;
- секреты и полные URL с подписями нельзя писать в логи;
- HTTPS по умолчанию; HTTP разрешать только явным opt-in для LAN/self-hosted адреса.

### Ответ

Отдельного JSON API нет: ответом является media body/manifest. Адаптер должен вернуть `VetroVideo(url, headers, label, sourceName)`.

### Stream/download

- Stream: да, если формат поддержан Media3.
- Download: да, когда пользователь владеет объектом или получил явное разрешение; progressive уже совместим с текущим downloader, HLS — совместим при обычных VOD-плейлистах, DASH требует отдельной реализации.

### Рекомендация

Первый адаптер: `DirectHttpPlaybackSource`. Это самый маленький и надёжный seam; он также становится транспортным фундаментом для WebDAV, S3 и временных URL медиасерверов.

## 2. WebDAV / Nextcloud

### Контракт

WebDAV использует `PROPFIND` для списка ресурсов и обычный `GET` для содержимого файла. Базовый стандарт — RFC 4918. [RFC 4918](https://datatracker.ietf.org/doc/html/rfc4918)

Для Nextcloud:

```text
PROPFIND /remote.php/dav/files/{user}/{folder}
GET      /remote.php/dav/files/{user}/{path/to/video.mp4}
```

Официальная документация подтверждает Basic/session-cookie auth, app password при 2FA/OIDC, свойства `getcontenttype`, `getcontentlength`, `etag` и скачивание файла через GET. Для shared-файла GET работает только если владелец не запретил скачивание. [Nextcloud WebDAV: basic file operations](https://docs.nextcloud.com/server/stable/developer_manual/client_apis/WebDAV/basic.html)

### Auth/config

- server base URL;
- username + app password (предпочтительно) или поддерживаемая сервером схема auth;
- root folder;
- TLS certificate policy без глобального отключения проверки сертификатов.

### Ответ

`PROPFIND` возвращает XML `207 Multi-Status`; для каждого файла нужны `href`, MIME type, length, ETag и display name. После выбора `href` используется как URL media body с теми же auth headers.

### Android feasibility

Высокая. OkHttp выполняет `PROPFIND` и GET, а существующий `VetroVideo.headers` передаёт Basic/Bearer auth в Media3. Progressive seek/resume зависит от поддержки byte-range конкретным WebDAV-сервером.

HLS/DASH возможны только если manifest и все относительные segment/key URL доступны с теми же заголовками. Один WebDAV MP4 намного надёжнее как первая версия.

### Stream/download

- Stream: да, непосредственно из GET.
- Download: да, если GET разрешён и share не имеет запрета download.

### Рекомендация

Реализовать вместе с Direct HTTP как `WebDavPlaybackSource`: browse/search только внутри указанного пользователем root, без глобального поиска чужого контента.

## 3. Jellyfin

### Почему подходит

Jellyfin — self-hosted медиасервер, предназначенный для управления и streaming медиатеки пользователя. [Jellyfin server repository](https://github.com/jellyfin/jellyfin)

У Jellyfin есть официальный Kotlin SDK. API instance принимает `baseUrl` и access token/API key; пользователь может войти по username/password, после чего `AuthenticationResult.accessToken` устанавливается в client. [Jellyfin Kotlin SDK: getting started](https://kotlin-sdk.jellyfin.org/guide/getting-started.html), [authentication](https://kotlin-sdk.jellyfin.org/guide/authentication.html)

### Auth/config

- server base URL;
- user login/token, привязанный к конкретному серверу;
- уникальные client/device данные;
- Android Network Security Config для явно разрешённого локального HTTP, если сервер не имеет TLS.

### Request/response

Рекомендуемый flow:

1. получить/найти item в пользовательской library;
2. `GET /Items/{itemId}/PlaybackInfo` или SDK `MediaInfoApi.getPlaybackInfo`;
3. выбрать `MediaSourceInfo`;
4. использовать direct stream URL либо `transcodingUrl`, сохраняя `requiredHttpHeaders` и `playSessionId`;
5. отправлять playback start/progress/stop в session API.

Официальная модель `PlaybackInfoResponse` содержит `mediaSources` и `playSessionId`. [PlaybackInfoResponse](https://kotlin-sdk.jellyfin.org/dokka/jellyfin-model/org.jellyfin.sdk.model.api/-playback-info-response/index.html)

`MediaSourceInfo` содержит `supportsDirectPlay`, `supportsDirectStream`, `supportsTranscoding`, `requiredHttpHeaders`, `transcodingUrl`, container/bitrate/track indices. [MediaSourceInfo](https://kotlin-sdk.jellyfin.org/dokka/jellyfin-model/org.jellyfin.sdk.model.api/-media-source-info/index.html)

Jellyfin различает HTTP и HLS как media streaming protocols. [MediaStreamProtocol](https://kotlin-sdk.jellyfin.org/dokka/jellyfin-model/org.jellyfin.sdk.model.api/-media-stream-protocol/index.html)

Для static/direct stream SDK генерирует URL `Videos/{itemId}/stream`/`stream.{container}` с параметрами `mediaSourceId`, `playSessionId`, codec/bitrate и `static`. [Jellyfin VideoApi](https://kotlin-sdk.jellyfin.org/dokka/jellyfin-api/org.jellyfin.sdk.api.operations/-video-api/index.html)

### Android feasibility

Очень высокая: официальный multiplatform Kotlin SDK + готовый Media3. `requiredHttpHeaders` напрямую переводятся в `VetroVideo.headers`, `transcodingUrl` — в `VetroVideo.url`.

Для правильного server-side выбора direct/transcode следует передавать device profile Media3, а не жёстко требовать MP4. На первой итерации допустимо предпочитать direct HTTP для поддерживаемых контейнеров и HLS-transcode как fallback.

### Stream/download

- Stream: да, direct stream или HLS-transcode.
- Download: официальный SDK имеет `LibraryApi.getDownload/getDownloadUrl`; item DTO содержит `canDownload`, а user policy — `enableContentDownloading`. Скачивание включать только когда эти capability/permission положительны. [Jellyfin LibraryApi](https://kotlin-sdk.jellyfin.org/dokka/jellyfin-api/org.jellyfin.sdk.api.operations/-library-api/index.html), [Jellyfin model API](https://kotlin-sdk.jellyfin.org/dokka/jellyfin-model/org.jellyfin.sdk.model.api/index.html)

### Рекомендация

Первый полноценный catalog-aware адаптер. Он может сопоставлять Vetro title/year/season/episode с пользовательской library и дополнительно валидировать provider IDs, если они заполнены сервером. Нельзя искать «Доктора Хауса» во внешнем интернете через Jellyfin: источник возвращает только содержимое подключённого сервера.

## 4. Emby

### Auth/config

Для мобильного пользователя нужен user authentication: `/Users/AuthenticateByName`, затем `X-Emby-Token` в запросах. Static API key рекомендован официальной документацией для server-to-server integrations, а не как общий пользовательский токен. [Emby user authentication](https://dev.emby.media/doc/restapi/User-Authentication.html), [API-key authentication](https://dev.emby.media/doc/restapi/API-Key-Authentication.html)

### Request/response

Flow близок к Jellyfin:

1. найти item в library;
2. `GET`/`POST /Items/{Id}/PlaybackInfo`;
3. получить `PlaybackInfoResponse { MediaSources[], PlaySessionId }`;
4. выбрать `DirectStreamUrl` или `TranscodingUrl`, применить `RequiredHttpHeaders`;
5. отправлять session check-ins.

Официальная модель включает direct/transcode capabilities, URL, container, bitrate, stream indices и required headers. [Emby PlaybackInfo](https://dev.emby.media/reference/RestAPI/MediaInfoService/getItemsByIdPlaybackinfo.html)

Direct stream выполняется через `GET /Videos/{Id}/stream.{Container}`; `static=true` отдаёт исходный файл без кодирования. [Emby VideoService](https://dev.emby.media/reference/RestAPI/VideoService/getVideosByIdStreamByContainer.html), [video streaming guide](https://dev.emby.media/doc/restapi/Video-Streaming.html)

Официальный playback guide описывает порядок Direct Play → Direct Stream → Transcode. [Emby playback guidelines](https://dev.emby.media/doc/restapi/Playback-Guidelines.html)

### Android feasibility

Высокая. REST JSON и те же HTTP headers/Media3 primitives, что у Jellyfin. Потребуется собственный Ktor/OkHttp client layer либо оценка лицензии/актуальности Emby SDK.

### Stream/download

- Stream: да.
- Download: `GET /Items/{Id}/Download`, требует user auth и может вернуть 403 при отсутствии разрешения. [Emby download endpoint](https://dev.emby.media/reference/RestAPI/LibraryService/getItemsByIdDownload.html)

### Рекомендация

Реализовать после Jellyfin через общий `PersonalMediaServerSource` contract, но не смешивать wire DTO двух серверов.

## 5. Plex Personal Media Server

### Auth/config

PMS API использует `X-Plex-Token` и `X-Plex-Client-Identifier`; новый клиент должен запрашивать JSON через `Accept: application/json`. Токен получается через plex.tv auth. [Plex Media Server API](https://developer.plex.tv/pms/)

### Request/response

Официальный API строится вокруг provider features и относительных `key`; ответы metadata содержат media/part данные, а реальные пути нужно брать из API response, не конструировать из недокументированных URL. В опубликованном PMS API playback/transcode workflow менее прямолинеен, чем Jellyfin/Emby, и требует отдельной проверки на поддерживаемой версии PMS.

### Android feasibility

Средняя. Native playback персонального контента возможен, но нужны Plex discovery/auth, server selection, connection fallback, client profile, transcode session и playback reporting. Это заметно больше work, чем Jellyfin.

### Stream/download

- Stream: да для personal PMS media при действительном token.
- Download: нельзя превращать `Part.key` в универсальную кнопку скачивания. Официальный Downloads требует personal media, Plex Pass у пользователя и разрешение `Allow Downloads` от владельца сервера; Plex-provided VOD/rentals скачивать нельзя. [Plex Downloads overview](https://support.plex.tv/articles/downloads-overview/), [Plex Downloads FAQ](https://support.plex.tv/articles/downloads-sync-faq/)

### Рекомендация

Поздний адаптер. Использовать только personal media и официальный permission-aware download workflow. Не подключать бесплатный Plex VOD как источник нативных URL.

## 6. Internet Archive

### Auth/config

Чтение публичной item metadata обычно не требует авторизации. `GET https://archive.org/metadata/{identifier}` возвращает JSON с `metadata` и `files[]`; у файла есть `name`, `source`, `format`, `size`, checksums. [Internet Archive Item Metadata Read API](https://archive.org/developers/md-read.html)

Постоянный download URL файла:

```text
https://archive.org/download/{identifier}/{filename}
```

Нельзя сохранять перенаправленный hostname вида `ia8....us.archive.org` как постоянный адрес. [Internet Archive archival URLs](https://archive.org/developers/items.html#archival-urls)

### Rights filter

Archive.org содержит не только public-domain материал. `licenseurl` — необязательное поле, задаваемое uploader; `rights` и `possible-copyright-status` тоже могут быть uploader metadata. [Internet Archive metadata schema: licenseurl/rights](https://archive.org/developers/metadata-schema/index.html#licenseurl)

Безопасное правило Vetro:

1. разрешать только curated allowlist коллекций/идентификаторов;
2. требовать известный `licenseurl` (CC0, CC BY, CC BY-SA и т.п.) или проверенное public-domain rights statement;
3. показывать лицензию и attribution рядом с источником;
4. не считать отсутствие copyright-поля разрешением;
5. не использовать access-restricted items.

### Response selection

Из `files[]` выбирать производный/исходный video format, совместимый с Media3, например MPEG4/WebM, с разумным размером. Сохранять checksum для проверки загрузки. `mediatype=movies` означает тип отображения/обработки, а не автоматически свободную лицензию. [Internet Archive metadata schema: mediatype](https://archive.org/developers/metadata-schema/index.html#mediatype)

### Stream/download

- Stream: да, progressive URL.
- Download: да только когда конкретная лицензия разрешает это использование; соблюдать attribution/share-alike/non-commercial ограничения.

### Рекомендация

Подключать как отдельную секцию «Свободные фильмы/архив», а не fallback для любого коммерческого TMDB title. Автоматическое fuzzy-сопоставление TMDB → случайный Archive item небезопасно.

## 7. Wikimedia Commons

Это дополнительный безопасный источник коротких/образовательных фильмов и свободно лицензированного видео.

MediaWiki `action=query&prop=imageinfo` возвращает file URL, MIME, media type и `extmetadata`; можно запросить только license/attribution fields через `iiextmetadatafilter`. [MediaWiki Imageinfo API](https://www.mediawiki.org/wiki/API:Imageinfo)

TimedMediaHandler предоставляет transcode status и timed text; Commons публикует downloadable WebM/Ogg transcodes. [TimedMediaHandler API](https://www.mediawiki.org/wiki/Extension:TimedMediaHandler/API), [Commons video/transcodes](https://commons.wikimedia.org/wiki/Commons:Video)

### Stream/download

- Stream: progressive WebM/Ogg; Media3 поддерживает WebM, но реальная decode support зависит от устройства.
- Download: да по лицензии конкретного файла с обязательной attribution/share-alike обработкой.

### Рекомендация

Адаптер после Internet Archive. Результат обязан хранить `license`, `licenseUrl`, `artist/credit` и canonical description page; нельзя терять attribution при offline download.

## 8. VK Видео

Официальная schema VK API (версия 5.199 в репозитории) показывает:

- `video.get` и `video.search` требуют user access token;
- ответ содержит `video_video_full`;
- `files` может содержать `mp4_144` … `mp4_2160` либо `external` player URL.

Источники: [VK API video methods schema](https://github.com/VKCOM/vk-api-schema/blob/master/video/methods.json), [video objects schema](https://github.com/VKCOM/vk-api-schema/blob/master/video/objects.json), [official VK Android SDK](https://github.com/VKCOM/vk-android-sdk).

Однако schema не предоставляет машинно-проверяемую лицензию фильма и не документирует `files.mp4_*` как разрешение на offline download. Глобальный `video.search("название фильма")` может вернуть пользовательскую нелицензированную загрузку.

### Допустимый узкий вариант

- пользователь входит через официальный VK SDK;
- источник показывает только явно выбранные собственные видео пользователя или allowlist официальных/лицензированных каналов;
- воспроизводятся только MP4 URLs, которые API вернул этому пользователю;
- отсутствует download, пока официальный договор/API capability не разрешает его;
- не обходятся access errors, expiry, ads или token restrictions.

### Рекомендация

Не включать VK в автоматический MOVIE/SERIES resolver по TMDB/КиноПоиск. Возможен отдельный opt-in «Мои/официальные видео VK» после юридической проверки условий API.

## 9. RUTUBE

RUTUBE официально разрешает встраивание публичных и «только по ссылке» видео через собственный iframe player. Документация описывает embed URL, параметры, player events и запрос `https://rutube.ru/api/play/options/{videoId}` для атрибутов. [RUTUBE: встраивание видео](https://rutube.ru/info/embed/)

Документированный play-options response технически может содержать HLS balancer URL. Но официальная интеграционная документация оформляет разрешённый сценарий вокруг RUTUBE player, его рекламы, событий и лицензионного статуса автора; она не выдаёт общее разрешение забрать HLS в сторонний Media3 player. Лицензионное соглашение отдельно описывает RUTUBE player/API как средство доступа к пользовательскому и партнёрскому контенту, включая защищённый контент. [RUTUBE player EULA](https://rutube.ru/info/eula/)

### Stream/download

- Safe embed: да, через официальный RUTUBE player/WebView или согласованный SDK.
- Нативный Media3 HLS resolver: только после письменного партнёрского/API разрешения, включающего правила ads, telemetry, geo/age/DRM.
- Download: публичного разрешённого offline-download API не найдено; не реализовывать.

### Рекомендация

RUTUBE не должен возвращать `VetroVideo` из `video_balancer` без отдельного договора. Если продукт допускает WebView, можно сделать отдельный `EmbeddedPlayback` тип, который не притворяется прямым потоком и не показывает кнопку скачивания.

## Предлагаемый порядок реализации

1. **DirectHttpPlaybackSource**
   - progressive/HLS/DASH;
   - per-source headers;
   - probe без утечки токенов;
   - download capability отдельно от stream capability.
2. **WebDavPlaybackSource**
   - PROPFIND browse/search в пользовательском root;
   - progressive playback/download;
   - Nextcloud app passwords.
3. **JellyfinPlaybackSource**
   - официальный Kotlin SDK;
   - library match → PlaybackInfo → direct/HLS;
   - download только при `canDownload` и user policy.
4. **EmbyPlaybackSource**
   - тот же domain contract, отдельный wire adapter.
5. **OpenMediaSource**
   - Internet Archive curated licenses;
   - Wikimedia Commons license + attribution.
6. **PlexPlaybackSource**
   - personal PMS only;
   - Downloads только через разрешённый Plex workflow.
7. **VK/RUTUBE**
   - не добавлять в автоматический resolver;
   - VK только как user-authenticated own/allowlisted videos без download;
   - RUTUBE только official embed до партнёрского соглашения.

## Рекомендуемый domain contract

```kotlin
interface MovieSeriesPlaybackSource {
    val id: String
    suspend fun find(request: PlaybackLookup): List<PlaybackCandidate>
    suspend fun resolve(candidate: PlaybackCandidate): PlaybackAsset
}

data class PlaybackAsset(
    val stream: VetroVideo,
    val capabilities: Set<PlaybackCapability>, // STREAM, DOWNLOAD
    val expiresAt: Instant? = null,
    val rights: PlaybackRights,
)

data class PlaybackRights(
    val basis: RightsBasis, // USER_OWNED, SERVER_PERMISSION, PUBLIC_DOMAIN, OPEN_LICENSE
    val licenseUrl: String? = null,
    val attribution: String? = null,
)
```

Ключевое правило: `DOWNLOAD` выставляет адаптер только из положительного capability/permission/licence evidence. Оркестратор не должен выводить возможность скачивания только из того, что URL технически доступен.

## Security checklist

- Не хранить credentials, access tokens, cookies и presigned query strings в `BuildConfig`, analytics или Logcat.
- Хранить user tokens отдельно по `sourceId + serverId`; поддерживать logout/revoke.
- Передавать auth headers и на HLS/DASH manifest, и на дочерние segment/key/subtitle requests.
- Не отключать TLS validation глобально ради self-signed сервера; использовать явный per-server trust flow.
- Ограничивать redirect policy, особенно чтобы `Authorization` не утекал на другой host.
- Не кешировать URL с коротким expiry как постоянный stream; повторно вызывать `resolve`.
- Проверять MIME/magic bytes перед установкой скачанного файла.
- Сохранять license/attribution вместе с offline-файлом для открытых каталогов.

## Что сознательно исключено

- Collaps, HDVB, Videoseed/VideoDB, Vibix, CDNVideoHub, Zetflix, Rezka, Filmix, KinoPub, VidSrc/VidLink и аналогичные embed/resolver сети;
- Lampac как источник обходных реализаций;
- извлечение HLS из iframe/JavaScript, обход anti-bot/Cloudflare/DRM;
- TMDB watch providers: это каталог доступности и ссылок, не API воспроизводимых потоков;
- YouTube/VK/RUTUBE global search как способ найти коммерческий фильм без проверки правообладателя.
