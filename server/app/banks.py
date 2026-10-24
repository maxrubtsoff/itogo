"""Bank metadata mirrored from the Android app's BankBrand catalogue."""

TITLES = {
    "sber": "Сбербанк",
    "tbank": "Т-Банк",
    "alfa": "Альфа-Банк",
    "yandex": "Яндекс Пэй",
    "mts": "МТС Деньги",
    "ozon": "Ozon Банк",
    "alfa_business": "Альфа-Бизнес",
    "tbank_business": "Т-Бизнес",
    "unknown": "Другой банк",
}

BUSINESS = {"alfa_business", "tbank_business"}


def title(bank: str) -> str:
    return TITLES.get(bank, TITLES["unknown"])


def kind(bank: str) -> str:
    return "business" if bank in BUSINESS else "personal"
