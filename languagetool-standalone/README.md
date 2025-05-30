# LanguageTool

**A proofreading tool for English, Spanish, German,
French, Portuguese, Dutch, Ukrainian
and [more languages](https://dev.languagetool.org/languages)**

Version 6.6 (2025-03-27)  
Copyright (C) 2005-2025 the LanguageTool community and Daniel Naber (www.danielnaber.de)  
https://languagetool.org


## Requirements

* Java 17 or later


## Usage

### Command-line version

To check plain text files from the command line, use

    java -jar languagetool-commandline.jar -l xx <filename>

with `xx` being the code for your language, e.g. `en-US` for American English
or just `en` for English without spell checking activated.

If you get a `java.lang.OutOfMemory` error, try increasing the Java
heap size as follows, where `4096` is the size in megabytes (use more
or less, depending on your file size and memory available).

    java -Xmx4096M -jar languagetool-commandline.jar -l xx <filename>

### Stand-alone version

To use the stand-alone version, double-click on the `languagetool.jar` file
or call `java -jar languagetool.jar` from the command line.

### HTTP API

See https://dev.languagetool.org/public-http-api and
https://languagetool.org/http-api/swagger-ui/#/default

### Java API

See https://dev.languagetool.org/java-api


## License
 
Unless otherwise noted, this software is distributed under
the LGPL, see file [COPYING.txt](https://github.com/languagetool-org/languagetool/blob/master/languagetool-standalone/COPYING.txt)

See [third-party-licenses/README.txt](https://github.com/languagetool-org/languagetool/blob/master/languagetool-standalone/src/main/resources/third-party-licenses/README.txt) for the copyright of the external libraries.

#### Frequency data

Some language's spelling dictionaries contain frequency data. This is taken
from the Mozilla-B2G Gaia project (https://github.com/mozilla-b2g/gaia/) which
again takes it from Spell On It (http://www.spellonit.com/downloads/frequencies/).
The frequency data is released under Creative Commons Attribution 4.0
International License (http://creativecommons.org/licenses/by/4.0/).

#### Asturian

See org/languagetool/resource/ast/README.txt and org/languagetool/resource/ast/hunspell/LICENSES*.txt

#### Belarusian

See org/languagetool/resource/be/hunspell/README.txt

#### Breton

See org/languagetool/resource/br/README.txt and See org/languagetool/resource/br/hunspell/README.txt

#### Catalan

See org/languagetool/resource/ca/README.txt

#### Chinese

See org/languagetool/resource/zh/README.txt

#### Danish

See org/languagetool/resource/da/README.txt and org/languagetool/resource/da/spelling/README_da_DK.txt

#### Dutch

See org/languagetool/resource/nl/README.txt and org/languagetool/resource/nl/spelling/README.txt
 
#### English

See org/languagetool/resource/en/pos-readme.txt and org/languagetool/resource/en/12dicts-readme.html

#### Esperanto

See org/languagetool/resource/eo/hunspell/README_eo.txt

#### French

See org/languagetool/resource/fr/README_lexique.txt and org/languagetool/resource/fr/hunspell/fr_FR.README

#### Galician

See org/languagetool/resource/gl/README.txt and org/languagetool/resource/gl/hunspell/README-gl-ES.txt
and LICENSES-en.txt

#### German

See org/languagetool/resource/de/README.txt and org/languagetool/resource/de/hunspell/*README.txt

#### Greek

See org/languagetool/resource/el/README.txt and org/languagetool/resource/el/hunspell/README_el_GR.txt

#### Italian

See org/languagetool/resource/it/README.txt and org/languagetool/resource/it/hunspell/README_it_IT.txt
 
#### Japanese

No POS or spelling data included in these sources.

#### Khmer

See org/languagetool/resource/km/README.txt

#### Malayalam (inactive)

See org/languagetool/resource/ml/README.txt

#### Persian

See org/languagetool/resource/km/README.txt
 
#### Polish

See org/languagetool/resource/pl/README.txt and org/languagetool/resource/pl/hunspell/README_en.txt

#### Portuguese

See org/languagetool/resource/pt/portuguese_dict_README and org/languagetool/resource/pt/hunspell/README*.txt

#### Romanian

See org/languagetool/resource/ro/README.txt and org/languagetool/resource/ro/hunspell/README_*.txt

#### Russian

See org/languagetool/resource/ru/README.txt and org/languagetool/resource/ru/hunspell/README.txt

#### Serbian

See org/languagetool/resource/sr/README.md and org/languagetool/resource/sr/dictionary/ekavian/README_hunspell.txt
and org/languagetool/resource/sr/dictionary/jekavian/README_hunspell.txt

#### Slovak

See org/languagetool/resource/sk/README.txt

#### Slovenian

See org/languagetool/resource/sl/hunspell/README_sl_SI.txt

#### Spanish

See  org/languagetool/resource/es/hunspell/README_es_ES.txt and org/languagetool/resource/es/README.txt

#### Swedish

See org/languagetool/resource/sv/hunspell/LICENSE*.txt and org/languagetool/resource/sv/README.txt
 
#### Tagalog

See org/languagetool/resource/tl/README.txt

#### Tamil

See org/languagetool/resource/ta/README.txt

#### Ukrainian

See org/languagetool/resource/uk/README.txt
