import unicodedata


def normalize(values, candidates):
    def key(value):
        return "".join(unicodedata.normalize("NFKC", value).split()).replace("#", "").casefold()

    known = {key(value): value for value in candidates}
    return list(dict.fromkeys(known.get(key(value), value) for value in values))
