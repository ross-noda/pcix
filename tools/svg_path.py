"""Expand SVG arc flags and implicit coordinates into Android-compatible path data."""
import re
NUMBER = re.compile(r'[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?')
ARITY = dict(zip('MLHVCSQTA', [2, 2, 1, 1, 6, 4, 4, 2, 7]))
def normalize_path(data):
    i = 0
    command = None
    result = []
    while i < len(data):
        while i < len(data) and data[i] in ' ,\t\r\n': i += 1
        if i == len(data): break
        if data[i].isalpha():
            command = data[i]; i += 1
            if command.upper() == 'Z':
                result.append(command); command = None; continue
        if command is None or command.upper() not in ARITY: raise ValueError(data)
        values = []
        for n in range(ARITY[command.upper()]):
            while i < len(data) and data[i] in ' ,\t\r\n': i += 1
            if command.upper() == 'A' and n in (3, 4):
                if i == len(data) or data[i] not in '01': raise ValueError(data)
                values.append(data[i]); i += 1
            else:
                match = NUMBER.match(data, i)
                if not match: raise ValueError(data)
                values.append(match.group()); i = match.end()
        result.append(command + ' '.join(values))
        if command == 'M': command = 'L'
        elif command == 'm': command = 'l'
    return ' '.join(result)
