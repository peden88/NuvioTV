# Anime Calendar TV

Native Android TV frontend for the self-hosted Anime Calendar at `https://anime.peden88.stream`.

## TV features

- Watching/watchlist view
- Full Calendar schedule
- Upcoming titles
- Current + upcoming season Browse view
- D-pad/remote-first focus navigation
- Short press: title details and watchlist action
- Long press: quick actions including **Open in Nuvio**
- Nuvio handoff uses `nuvio://search?q=<title>&open=detail`
- Stable package: `com.peden88.animecalendar.tv`

The client is defensive about Anime Calendar JSON field names and watchlist mutation forms so it can work across the current Python calendar API and minor backend revisions.
