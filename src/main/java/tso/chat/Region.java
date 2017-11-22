package tso.chat;

public enum Region {
    ARGENTINA("Argentina"),
    BRAZIL("Brazil"),
    CHILE("Chile"),
    COLOMBIA("Colombia"),
    COSTA_RICA("Costa Rica"),
    CZECH_REPUBLIC("Czech Republic"),
    ECUADOR("Ecuador"),
    EUROPE("Europe"),
    FRANCE("France"),
    GERMANY("Germany"),
    GREECE("Greece"),
    ITALY("Italy"),
    LATIN_AMERICA("Latin America"),
    MEXICO("Mexico"),
    NETHERLANDS("Netherlands"),
    PERU("Peru"),
    POLAND("Poland"),
    ROMANIA("Romania"),
    RUSSIA("Russia"),
    SPAIN("Spain"),
    USA("USA"),
    URUGUAY("Uruguay"),
    VENEZUELA("Venezuela");

    String value;
    Region(String value) {
        this.value=value;
    };
}
