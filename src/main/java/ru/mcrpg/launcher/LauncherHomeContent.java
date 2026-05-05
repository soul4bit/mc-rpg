package ru.mcrpg.launcher;

import java.util.ArrayList;
import java.util.List;

public final class LauncherHomeContent {

    private String heroEyebrow;
    private String heroTitle;
    private String heroDescription;
    private String heroFootnote;
    private List<SpotlightCard> spotlight = new ArrayList<SpotlightCard>();
    private List<NewsEntry> news = new ArrayList<NewsEntry>();

    public static LauncherHomeContent defaults() {
        LauncherHomeContent content = new LauncherHomeContent();
        content.setHeroEyebrow("FEATURED");
        content.setHeroTitle("Redstone Realm");
        content.setHeroDescription(
            "Один основной сервер, одна сборка и короткий путь от синхронизации модпака до входа в игру."
        );
        content.setHeroFootnote(
            "Баннеры, витрина и лента новостей теперь живут отдельно от backend-логики запуска."
        );

        List<SpotlightCard> spotlightCards = new ArrayList<SpotlightCard>();
        spotlightCards.add(new SpotlightCard(
            "SERVER",
            "Основной мир",
            "Один адрес, один профиль и готовый вход через синхронизированный клиент.",
            "fire"
        ));
        spotlightCards.add(new SpotlightCard(
            "MODPACK",
            "One-click sync",
            "Manifest, runtime, Forge и client files поднимаются без ручной подготовки.",
            "copper"
        ));
        spotlightCards.add(new SpotlightCard(
            "RUNTIME",
            "Portable Java",
            "Лаунчер может держать локальную JRE внутри клиента, а не зависеть от системы.",
            "emerald"
        ));
        content.setSpotlight(spotlightCards);

        List<NewsEntry> newsEntries = new ArrayList<NewsEntry>();
        newsEntries.add(new NewsEntry(
            "NOW",
            "Игрок видит только нужное",
            "На главном экране оставлены только профиль, папка клиента, запуск и live log."
        ));
        newsEntries.add(new NewsEntry(
            "SYNC",
            "Сборка обновляется перед запуском",
            "Если автообновление включено, лаунчер сначала сверяет manifest и только потом открывает Minecraft."
        ));
        newsEntries.add(new NewsEntry(
            "ACCESS",
            "Технические поля спрятаны",
            "Java, manifest URL и launch template остаются в отдельном окне, а не на основной поверхности."
        ));
        content.setNews(newsEntries);

        return content;
    }

    public String getHeroEyebrow() {
        return heroEyebrow;
    }

    public void setHeroEyebrow(String heroEyebrow) {
        this.heroEyebrow = sanitize(heroEyebrow);
    }

    public String getHeroTitle() {
        return heroTitle;
    }

    public void setHeroTitle(String heroTitle) {
        this.heroTitle = sanitize(heroTitle);
    }

    public String getHeroDescription() {
        return heroDescription;
    }

    public void setHeroDescription(String heroDescription) {
        this.heroDescription = sanitize(heroDescription);
    }

    public String getHeroFootnote() {
        return heroFootnote;
    }

    public void setHeroFootnote(String heroFootnote) {
        this.heroFootnote = sanitize(heroFootnote);
    }

    public List<SpotlightCard> getSpotlight() {
        return spotlight;
    }

    public void setSpotlight(List<SpotlightCard> spotlight) {
        this.spotlight = spotlight == null ? new ArrayList<SpotlightCard>() : spotlight;
    }

    public List<NewsEntry> getNews() {
        return news;
    }

    public void setNews(List<NewsEntry> news) {
        this.news = news == null ? new ArrayList<NewsEntry>() : news;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value;
    }

    public static final class SpotlightCard {

        private String eyebrow;
        private String title;
        private String copy;
        private String accent;

        public SpotlightCard() {
        }

        public SpotlightCard(String eyebrow, String title, String copy, String accent) {
            setEyebrow(eyebrow);
            setTitle(title);
            setCopy(copy);
            setAccent(accent);
        }

        public String getEyebrow() {
            return eyebrow;
        }

        public void setEyebrow(String eyebrow) {
            this.eyebrow = sanitize(eyebrow);
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = sanitize(title);
        }

        public String getCopy() {
            return copy;
        }

        public void setCopy(String copy) {
            this.copy = sanitize(copy);
        }

        public String getAccent() {
            return accent;
        }

        public void setAccent(String accent) {
            this.accent = sanitize(accent);
        }
    }

    public static final class NewsEntry {

        private String tag;
        private String title;
        private String copy;

        public NewsEntry() {
        }

        public NewsEntry(String tag, String title, String copy) {
            setTag(tag);
            setTitle(title);
            setCopy(copy);
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = sanitize(tag);
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = sanitize(title);
        }

        public String getCopy() {
            return copy;
        }

        public void setCopy(String copy) {
            this.copy = sanitize(copy);
        }
    }
}
