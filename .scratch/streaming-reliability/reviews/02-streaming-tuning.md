# TICKET-02 review

## Standards

Первый проход потребовал переименовать вводящий в заблуждение `HeaderResolvingPlayerFactory`.
Module и файл стали `StreamingPlaybackSessionFactory` / `StreamingPlaybackSession.kt`; KDoc отражает
полную роль session seam, header resolution скрыт. Финальный проход: blockers/important отсутствуют.

## Spec

Без замечаний: 60/90/2/6, time-over-size, ABR 25/10/25, 0×0, 0.60, один instrumented
DataSource factory; local player не затронут.

## Architecture observer

Две разъехавшиеся MediaSource factories заменены одним глубоким session module.

