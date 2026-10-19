"""Сборка silero_be.json — вход для stress.bin белорусской сборки.

Символы, алфавит и номера дикторов берутся ИЗ ПАКА (dist/ruvoice-pack-cis_be.zip),
чтобы таблица символов совпадала с моделью один в один. Остальное — белорусское:
вопросительные слова, вводные слова, разметка «ё» пустая (канал «ё» у bel-акцентора
отсутствует), русских таблиц ударений (AOT, BERT, eyo) нет вовсе.
"""
import json
import os
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
PACK = os.path.join(ROOT, "dist", "ruvoice-pack-cis_be.zip")
OUT = os.path.join(ROOT, "app/src/main/assets/silero/silero_be.json")

WH = ["хто", "што", "дзе", "калі", "чаму", "навошта", "як", "які", "якая", "якое", "якія",
      "чый", "чыя", "чыё", "чыі", "колькі", "куды", "адкуль", "каторы", "чым", "каго", "чаго"]
FILLERS = ["ну", "вось", "а", "дык", "мо", "мабыць", "нават", "проста", "значыць", "так", "але", "ці"]
# метки хвоста вопроса: «…, праўда?», «…, ці не так?»
TAGS = ["\\bня\\s+прауда\\b", "\\bня\\s+прауд[аы]\\b", "\\bці\\s+не\\s+так\\b", "\\bтак\\s+жа\\b"]

BODY = {
    "lang": "be",
    "types": False,
    "bases": {},
    "type2id": {},
    "wh_forms": WH,
    "leading_fillers": FILLERS,
    "tag_patterns": TAGS,
    "bert": {"vocab": {}, "cls": 0, "sep": 1, "pad": 0, "unk": 0, "homo_start": 0, "homo_end": 0, "max_len": 128},
    "exceptions": {},
    "homodict": {},
    "phrases": {},
    "gram": {},
}


def main():
    with zipfile.ZipFile(PACK) as z:
        pack = json.loads(z.read("pack.json"))
    data = dict(BODY)
    data.update({
        "symbols": pack["symbols"],
        "symbol_to_id": pack["symbol_to_id"],
        "sos": pack["sos"],
        "eos": pack["eos"],
        "alphabet": pack["alphabet"],
        "speakers": pack["speakers"],
    })
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    print("готово:", OUT, round(os.path.getsize(OUT) / 1024, 1), "КБ")
    print("голоса:", data["speakers"])
    print("символов:", len(data["symbols"]), "| алфавит:", data["alphabet"][:12], "...")


if __name__ == "__main__":
    main()
