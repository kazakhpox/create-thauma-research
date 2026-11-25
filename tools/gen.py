#!/usr/bin/env python3
"""
Writes the Mechanoturgy chapter: the Thaumonomicon category, its research entries (in Thaumaturge's own data format), their texts in
English and Russian, and the lock list (which Create items each research unlocks).

Usage: gen.py <create jar> <thaumaturge jar> <resources dir>

Each entry is a thought of the thaumaturge that follows from the one before it, as Thaumaturge's own entries are; it asks for the
same kinds of things (observations and theories of a chapter, scans of an aspect, items in hand, other research) and opens the
Create machines it is about. Knowledge is asked of Thaumaturge's own chapters, so the points are earned the usual way.
"""
import io
import json
import os
import shutil
import sys
import zipfile

from PIL import Image, ImageEnhance

NS = 'createthaumaresearch'
CAT = NS + ':mechanoturgy'
TH = 'thaumaturge:'


def obs(cat, n=1):
    return {'type': 'observation', 'category': TH + cat, 'amount': n}


def theory(cat, n=1):
    return {'type': 'theory', 'category': TH + cat, 'amount': n}


def scan(aspect):
    return TH + 'scanned/aspect/thaumaturge/' + aspect


def hold(item, n=1):
    return {'items': [item], 'amount': n}


# id: dict(col,row, parents, icon, meta, aspects, reqs (list of stage requirements: knowledge, research, obtain), items it opens,
#          en/ru: (title, [stage texts]))
ENTRIES = {
    'base_mechanoturgy': dict(
        col=0, row=0, parents=[NS + ':unlock_mechanoturgy'], icon='create:andesite_alloy', meta=['round', 'hidden'],
        aspects={'machina': 3, 'motus': 2, 'metallum': 2}, stages=[{}],
        items=['andesite_alloy', 'andesite_alloy_block', 'wrench'],
        en=('Mechanoturgy', ['Andesite, bound with iron, holds a shape the way living stone never does. It is the first material of '
                             'machines that need no vis: an alloy that does not tire, a key that turns them.']),
        ru=('Механотургия', ['Андезит, связанный железом, держит форму так, как не держит её живой камень. Это первый материал машин, '
                             'которым не нужен вис: сплав, который не устаёт, и ключ, которым их поворачивают.'])),
    'kinetics': dict(
        col=0, row=2, parents=[NS + ':base_mechanoturgy'], icon='create:cogwheel',
        aspects={'motus': 6, 'aer': 3, 'ordo': 3},
        stages=[{'required_knowledge': [obs('artifice')], 'required_research': [scan('motus')]}, {}],
        items=['shaft', 'cogwheel', 'hand_crank', 'andesite_casing'],
        en=('The World Moves', ['Everything around me moves: water runs, a pendulum swings, a cart rolls. Motion can be watched, and '
                                'what can be watched can be understood.',
                                'A turning rod passes its motion on, a toothed wheel hands it to the next. With a crank I can be '
                                'the first mover myself; cased in andesite, the turning is safe to stand beside.']),
        ru=('Мир движется', ['Всё вокруг движется: вода течёт, маятник качается, вагонетка катится. Движение можно наблюдать, а '
                             'то, что можно наблюдать, можно и понять.',
                             'Вращающийся стержень передаёт движение дальше, зубчатое колесо отдаёт его соседу. Рукояткой я сам '
                             'могу стать первым двигателем, а в андезитовом корпусе вращение безопасно.'])),
    'water_power': dict(
        col=-2, row=3, parents=[NS + ':kinetics'], icon='create:water_wheel',
        aspects={'aqua': 5, 'motus': 4, 'terra': 2},
        stages=[{'required_knowledge': [obs('basics')], 'required_research': [scan('aqua')],
                 'obtain': [hold('minecraft:water_bucket')]}, {}],
        items=['water_wheel', 'large_water_wheel'],
        en=('The Strength of Water', ['A river never rests. If a wheel stood in its way, the river would turn it for me, day and '
                                       'night, asking nothing.',
                                       'Paddles on a wheel, the wheel on a shaft: falling and flowing water now turn my machines. The '
                                       'larger the wheel, the slower and stronger it turns.']),
        ru=('Сила потока', ['Река никогда не отдыхает. Если поставить на её пути колесо, она будет вращать его для меня день и '
                            'ночь, ничего не прося взамен.',
                            'Лопасти на колесе, колесо на валу: падающая и текущая вода теперь вращает мои машины. Чем больше '
                            'колесо, тем медленнее и сильнее оно крутится.'])),
    'wind_power': dict(
        col=-4, row=4, parents=[NS + ':water_power'], icon='create:windmill_bearing',
        aspects={'aer': 6, 'motus': 4, 'machina': 2},
        stages=[{'required_knowledge': [theory('artifice')], 'required_research': [scan('aer')]}, {}],
        items=['windmill_bearing', 'mechanical_bearing', 'white_sail', 'sail_frame'],
        en=('The Strength of Wind', ['Aer is the most restless of the primals. Where water must be led, wind comes by itself.',
                                      'A bearing that turns whatever is fixed to it, and sails to catch the wind: a whole structure '
                                      'becomes one great rotor.']),
        ru=('Сила ветра', ['Aer — самый беспокойный из первичных аспектов. Воду нужно подводить, а ветер приходит сам.',
                           'Подшипник, который вращает всё, что к нему прикреплено, и паруса, ловящие ветер: целая постройка '
                           'становится одним большим ротором.'])),
    'transmission': dict(
        col=2, row=3, parents=[NS + ':kinetics'], icon='create:large_cogwheel',
        aspects={'motus': 4, 'potentia': 4, 'ordo': 2},
        stages=[{'required_knowledge': [theory('basics'), obs('artifice')]}, {}],
        items=['large_cogwheel', 'gearbox', 'vertical_gearbox', 'belt_connector', 'encased_chain_drive', 'adjustable_chain_gearshift'],
        en=('Kinetic Energy', ['Turning carries something, the way a current carries essentia. It can be bent around corners, '
                                'sped up or slowed down, and every machine takes its share.',
                                'A large wheel against a small one changes the speed; a box of gears turns the motion aside; a '
                                'belt or a chain carries it far.']),
        ru=('Кинетическая энергия', ['Вращение что-то несёт, как поток несёт эссенцию. Его можно повернуть за угол, ускорить или '
                                      'замедлить, и каждая машина забирает свою долю.',
                                      'Большая шестерня против малой меняет скорость, коробка передач поворачивает движение в '
                                      'сторону, а ремень или цепь уносят его далеко.'])),
    'measuring': dict(
        col=4, row=2, parents=[NS + ':transmission'], icon='create:goggles',
        aspects={'sensus': 4, 'machina': 3, 'cognitio': 2},
        stages=[{'required_knowledge': [obs('artifice')], 'obtain': [hold(TH + 'goggles_revealing')]}, {}],
        items=['goggles', 'speedometer', 'stressometer'],
        en=('Measuring Rotation', ['My goggles of revealing show me the aspects of things, but not how hard a shaft strains. '
                                     'Another lens is needed.',
                                     'Engineer\'s goggles show speed and stress at a glance; dials on the shaft show them to anyone '
                                     'passing by.']),
        ru=('Измерение вращения', ['Мои очки откровения показывают аспекты вещей, но не то, как сильно напряжён вал. Нужна '
                                   'другая линза.',
                                   'Инженерные очки показывают скорость и нагрузку с одного взгляда, а приборы на валу — любому, '
                                   'кто проходит мимо.'])),
    'signals': dict(
        col=6, row=4, parents=[NS + ':transmission', TH + 'redstone_relay'], icon='create:redstone_link',
        aspects={'potentia': 4, 'machina': 3, 'sensus': 2},
        stages=[{'required_knowledge': [theory('artifice')], 'obtain': [hold('minecraft:redstone_block')]}, {}],
        items=['clutch', 'gearshift', 'sequenced_gearshift', 'redstone_link', 'analog_lever', 'pulse_repeater', 'pulse_timer',
               'powered_latch', 'powered_toggle_latch', 'redstone_contact', 'nixie_tube', 'display_link', 'display_board'],
        en=('Governing by Signal', ['A machine that cannot be stopped is a danger. The relay taught me how redstone can carry a '
                                     'will across a distance.',
                                     'A clutch cuts the motion, a gearshift reverses it, a link carries a signal through the air. '
                                     'Boards and tubes tell me what my machines are doing.']),
        ru=('Управление сигналом', ['Машина, которую нельзя остановить, опасна. Реле научило меня, как красный камень переносит '
                                    'волю на расстояние.',
                                    'Сцепление разрывает движение, реверсивная передача обращает его, передатчик несёт сигнал по '
                                    'воздуху. Табло и индикаторы говорят мне, чем заняты машины.'])),
    'tools': dict(
        col=0, row=5, parents=[NS + ':transmission'], icon='create:mechanical_drill',
        aspects={'instrumentum': 5, 'metallum': 3, 'perditio': 2},
        stages=[{'required_knowledge': [theory('artifice')], 'obtain': [hold('minecraft:iron_ingot')]}, {}],
        items=['mechanical_drill', 'mechanical_saw', 'mechanical_harvester', 'mechanical_plough'],
        en=('Tools of Labour', ['Iron and motion together make a tool that never grows tired in the hand.',
                                 'A drill that bites stone, a saw that fells trees, a harvester and a plough: the shaft does the '
                                 'work my arms used to.']),
        ru=('Орудия труда', ['Железо и движение вместе дают орудие, которое никогда не устаёт в руке.',
                             'Бур, грызущий камень, пила, валящая деревья, комбайн и плуг: вал делает работу, которую раньше '
                             'делали мои руки.'])),
    'processing': dict(
        col=0, row=7, parents=[NS + ':tools', TH + 'bellows'], icon='create:mechanical_press',
        aspects={'machina': 6, 'motus': 3, 'instrumentum': 3},
        stages=[{'required_knowledge': [obs('artifice')], 'required_research': [scan('machina')]}, {}],
        items=['mechanical_press', 'basin', 'mechanical_mixer', 'whisk', 'millstone', 'encased_fan', 'propeller', 'nozzle'],
        en=('The Mechanism', ['Rotation and a tool add up to a mechanism. Machina is the spark that makes the iron seem alive.',
                              'A press that flattens, a mixer that stirs a basin, a millstone that grinds, a fan that blows '
                              'through fire or water: the crafts of the workshop, done by turning.']),
        ru=('Механизм', ['Вращение и орудие складываются в механизм. Machina — искра, от которой железо кажется живым.',
                         'Пресс, который расплющивает, миксер над чашей, жёрнов, который мелет, вентилятор, дующий сквозь огонь '
                         'и воду: ремёсла мастерской, сделанные вращением.'])),
    'logistics': dict(
        col=-3, row=7, parents=[NS + ':tools'], icon='create:andesite_funnel',
        aspects={'motus': 4, 'ordo': 4, 'vacuos': 2},
        stages=[{'required_knowledge': [theory('basics')], 'required_research': [scan('ordo')]}, {}],
        items=['depot', 'chute', 'andesite_funnel', 'andesite_tunnel', 'item_vault', 'weighted_ejector'],
        en=('Logistics', ['Things must move too, not only shafts. A hungry chest swallows what is thrown at it; I want things to '
                          'go where I send them.',
                          'Belts and chutes carry, funnels take and give, a vault holds what a hundred chests would, and an '
                          'ejector throws things across the room.']),
        ru=('Логистика', ['Двигаться должны и вещи, а не только валы. Голодный сундук глотает то, что в него бросили, а я хочу, '
                          'чтобы вещи шли туда, куда я их отправил.',
                          'Ремни и жёлобы несут, шлюзы забирают и отдают, хранилище вмещает то, что сотня сундуков, а катапульта '
                          'перебрасывает вещи через комнату.'])),
    'brass': dict(
        col=3, row=8, parents=[NS + ':processing', TH + 'metallurgy'], icon='create:brass_ingot',
        aspects={'metallum': 5, 'alkimia': 3, 'ordo': 2},
        stages=[{'required_knowledge': [obs('alchemy')], 'obtain': [hold('create:brass_ingot')]}, {}],
        items=['brass_casing', 'electron_tube', 'brass_hand', 'sand_paper', 'red_sand_paper'],
        en=('Brass', ['I make brass in the crucible from a nugget and an aspect. The mixer makes it from copper and zinc, with heat '
                      'and patience. The metal is the same; the road is not.',
                      'Brass cases the finer machines, and a tube of rose quartz carries a signal through them.']),
        ru=('Латунь', ['Я делаю латунь в тигле из самородка и аспекта. Миксер делает её из меди и цинка, жаром и терпением. '
                       'Металл тот же, путь — другой.',
                       'Латунью одевают тонкие машины, а лампа из розового кварца проводит сквозь них сигнал.'])),
    'blaze_heat': dict(
        col=5, row=7, parents=[NS + ':processing', TH + 'infernal_furnace'], icon='create:empty_blaze_burner',
        aspects={'ignis': 5, 'bestia': 3, 'alkimia': 2},
        stages=[{'required_knowledge': [obs('alchemy')], 'required_research': [scan('ignis')]}, {}],
        items=['empty_blaze_burner'],
        en=('Blaze Fire', ['The Infernal Furnace taught me that the Nether keeps a better fire. A blaze, caged and fed, would keep '
                           'it burning under my basin.',
                           'An empty burner waits for a blaze. Fed with fuel, it heats the basin above for mixing and pressing '
                           'that ordinary fire cannot do.']),
        ru=('Пламя всполоха', ['Адская печь научила меня, что Нижний мир хранит огонь получше. Всполох в клетке, если его кормить, '
                               'будет поддерживать огонь под моей чашей.',
                               'Пустая горелка ждёт всполоха. Накормленная топливом, она нагревает чашу над собой для смешивания '
                               'и прессования, на которые не способен обычный огонь.'])),
    'fluids': dict(
        col=2, row=10, parents=[NS + ':processing', TH + 'tubes'], icon='create:fluid_pipe',
        aspects={'aqua': 5, 'vitreus': 3, 'ordo': 2},
        stages=[{'required_knowledge': [obs('alchemy')], 'obtain': [hold(TH + 'phial')]}, {}],
        items=['fluid_pipe', 'mechanical_pump', 'fluid_tank', 'fluid_valve', 'smart_fluid_pipe', 'spout', 'item_drain', 'hose_pulley',
               'portable_fluid_interface'],
        en=('Liquids', ['Essentia flows through my tubes by suction. Water, lava and honey could flow the same way, pushed by a '
                        'turning pump instead.',
                        'Pipes, a pump and tanks; a spout to fill, a drain to empty, a pulley to drink a lake dry.']),
        ru=('Жидкости', ['Эссенция течёт по моим трубкам за счёт всасывания. Вода, лава и мёд могли бы течь так же, только '
                         'толкаемые вращающимся насосом.',
                         'Трубы, насос и баки; дозатор, чтобы наполнять, осушитель, чтобы опустошать, и лебёдка, чтобы выпить '
                         'озеро досуха.'])),
    'steam': dict(
        col=5, row=10, parents=[NS + ':blaze_heat', NS + ':fluids'], icon='create:steam_engine',
        aspects={'ignis': 5, 'aqua': 4, 'machina': 3},
        stages=[{'required_knowledge': [obs('alchemy'), obs('artifice')], 'required_research': [scan('ignis')],
                 'obtain': [hold('minecraft:bucket')]}, {}],
        items=['steam_engine', 'steam_whistle', 'flywheel'],
        en=('The Steam Boiler', ['Fire and water together make steam, and steam pushes harder than any river.',
                                 'A tank over blaze burners becomes a boiler; engines on its sides turn a great flywheel. A '
                                 'whistle tells how hard it works.']),
        ru=('Паровой котёл', ['Огонь и вода вместе дают пар, а пар толкает сильнее любой реки.',
                              'Бак над горелками всполоха становится котлом, двигатели на его стенках вращают большой маховик. '
                              'Свисток подсказывает, как тяжело он работает.'])),
    'smog': dict(
        col=7, row=11, parents=[NS + ':steam', TH + 'flux'], icon='create:steam_whistle', meta=['hidden'],
        aspects={'vitium': 5, 'perditio': 3}, stages=[{}], items=[],
        en=('Overload and Smog', ['My boilers work day and night, and the air around them has changed. Too much fuel and too much '
                                   'turning throw off something worse than smoke: flux. Where flux gathers, the taint follows. I '
                                   'must keep my machines in measure.']),
        ru=('Перегрузка и смог', ['Мои котлы работают день и ночь, и воздух вокруг них изменился. Избыток топлива и вращения '
                                  'выбрасывает кое-что похуже дыма — поток. Где собирается поток, туда приходит порча. Я должен '
                                  'держать машины в мере.'])),
    'sorting': dict(
        col=-3, row=10, parents=[NS + ':logistics', NS + ':brass'], icon='create:mechanical_arm',
        aspects={'ordo': 5, 'cognitio': 3, 'machina': 2},
        stages=[{'required_knowledge': [theory('artifice')], 'required_research': [scan('cognitio')]}, {}],
        items=['filter', 'attribute_filter', 'brass_funnel', 'brass_tunnel', 'smart_chute', 'mechanical_arm', 'content_observer',
               'stockpile_switch'],
        en=('Sorting', ['Golems know which item is which because I put a seal on them. A brass funnel could know it too, if I '
                        'gave it a filter.',
                        'Filters choose, brass funnels and tunnels obey them, and a mechanical arm picks things up and puts them '
                        'down where they belong.']),
        ru=('Сортировка', ['Големы знают, какой предмет какой, потому что я ставлю на них печать. Латунный шлюз тоже мог бы это '
                           'знать, если дать ему фильтр.',
                           'Фильтры выбирают, латунные шлюзы и туннели им подчиняются, а механическая рука берёт вещи и кладёт '
                           'их туда, где им место.'])),
    'precision': dict(
        col=3, row=12, parents=[NS + ':brass'], icon='create:precision_mechanism',
        aspects={'machina': 5, 'ordo': 4, 'instrumentum': 3},
        stages=[{'required_knowledge': [theory('artifice'), obs('artifice')], 'obtain': [hold(TH + 'mechanism_complex')]}, {}],
        items=['deployer', 'mechanical_crafter', 'rotation_speed_controller', 'crafter_slot_cover'],
        en=('The Precision Mechanism', ['My complex mechanism is made by hand, gear by gear. A row of machines could put one '
                                         'together the same way, step after step.',
                                         'A deployer uses what it holds as a hand would; crafters work as one big crafting '
                                         'table; a controller sets the speed exactly.']),
        ru=('Механизм точности', ['Мой сложный механизм собирается руками, шестерня за шестернёй. Ряд машин мог бы собрать '
                                  'такой же, шаг за шагом.',
                                  'Автономный активатор использует то, что держит, как рука; сборщики работают как один большой '
                                  'верстак; регулятор задаёт скорость точно.'])),
    'contraptions': dict(
        col=0, row=11, parents=[NS + ':processing', TH + 'levitator'], icon='create:mechanical_piston',
        aspects={'motus': 5, 'vinculum': 3, 'machina': 3},
        stages=[{'required_knowledge': [theory('artifice')], 'required_research': [scan('vinculum')]}, {}],
        items=['mechanical_piston', 'sticky_mechanical_piston', 'piston_extension_pole', 'gantry_shaft', 'gantry_carriage',
               'rope_pulley', 'elevator_pulley', 'linear_chassis', 'radial_chassis', 'super_glue', 'sticker', 'cart_assembler',
               'contraption_controls', 'portable_storage_interface', 'turntable'],
        en=('Moving Structures', ['My levitator lifts things, but lets them fall. A piston could carry a whole wall and set it down '
                                  'where I want it.',
                                  'Pistons push, pulleys lift, carriages slide along a shaft; glue and chassis hold the blocks '
                                  'together while they travel.']),
        ru=('Движущиеся конструкции', ['Мой левитатор поднимает вещи, но роняет их. Поршень мог бы унести целую стену и '
                                       'поставить её туда, куда я хочу.',
                                       'Поршни толкают, лебёдки поднимают, каретки скользят по валу; клей и шасси держат блоки '
                                       'вместе, пока те в пути.'])),
    'schematics': dict(
        col=6, row=13, parents=[NS + ':precision'], icon='create:schematicannon',
        aspects={'cognitio': 4, 'fabrico': 4, 'ordo': 2},
        stages=[{'required_knowledge': [theory('basics'), theory('artifice')], 'obtain': [hold('minecraft:writable_book')]}, {}],
        items=['schematic_table', 'schematicannon', 'empty_schematic', 'schematic_and_quill', 'clipboard', 'crafting_blueprint'],
        en=('Schematics', ['A building is a pattern, the same way a research note is. If I could write the pattern down, a machine '
                           'could read it and build.',
                           'The quill draws a schematic, the table copies it, and the schematicannon fires blocks into place '
                           'one by one.']),
        ru=('Схематика', ['Постройка — это узор, так же как записка исследования. Если записать узор, машина сможет прочесть его '
                          'и построить.',
                          'Перо чертит схематику, стол переписывает её, а строительная пушка выстреливает блоки на свои места '
                          'один за другим.'])),
    'trains': dict(
        col=0, row=14, parents=[NS + ':contraptions', NS + ':precision'], icon='create:track_station',
        aspects={'motus': 6, 'machina': 4, 'vinculum': 3},
        stages=[{'required_knowledge': [theory('artifice', 2)], 'obtain': [hold('minecraft:rail')]}, {}],
        items=['track_station', 'track_signal', 'track_observer', 'controls', 'schedule', 'railway_casing'],
        en=('The Railway', ['A moving structure that follows a track could cross the world. A train is a contraption that goes '
                            'somewhere.',
                            'Tracks and stations, signals that keep trains apart, controls and a schedule for a train that '
                            'drives itself.']),
        ru=('Железная дорога', ['Движущаяся конструкция, идущая по пути, могла бы пересечь весь мир. Поезд — это штуковина, '
                                'которая куда-то едет.',
                                'Пути и станции, светофоры, разводящие поезда, контроллер и расписание для поезда, который '
                                'ведёт себя сам.'])),
    'packaging': dict(
        col=-3, row=13, parents=[NS + ':sorting'], icon='create:package_frogport',
        aspects={'ordo': 5, 'permutatio': 3, 'desiderium': 2},
        stages=[{'required_knowledge': [theory('artifice'), obs('golemancy')], 'obtain': [hold('minecraft:paper')]}, {}],
        items=['packager', 'repackager', 'package_frogport', 'stock_link', 'stock_ticker', 'factory_gauge', 'redstone_requester',
               'package_filter', 'cardboard_block'],
        en=('Packages and Orders', ['Golems carry what I ask for. A network of machines could do the same: wrap the goods, send '
                                    'them, and keep count of what is left.',
                                    'A packager wraps, a frogport sends and receives, the stock ticker counts and orders, and '
                                    'factory gauges keep a whole workshop supplied.']),
        ru=('Посылки и заказы', ['Големы приносят то, что я прошу. Сеть машин могла бы делать то же самое: упаковывать товар, '
                                 'отправлять его и вести учёт того, что осталось.',
                                 'Упаковщик упаковывает, квакопорт отправляет и принимает, складской тикер считает и заказывает, '
                                 'а фабричные контроллеры снабжают всю мастерскую.'])),
}

UNLOCK = dict(
    en=('Mechanoturgy', ['My arcane devices run on vis. But a waterwheel turns without vis, and a mill grinds without it. There may '
                         'be a craft of machines that needs no magic at all, only motion.',
                         'Motion, metal and order: Mechanoturgy is its own discipline now, with a chapter of its own.']),
    ru=('Открытие механотургии', ['Мои мистические устройства работают на вис. Но мельничное колесо вращается без вис, и жёрнов '
                                  'мелет без него. Возможно, есть ремесло машин, которому магия вовсе не нужна, — только '
                                  'движение.',
                                  'Движение, металл и порядок: механотургия теперь отдельная дисциплина со своей главой.']))


def recipes_for(create, items):
    """Every Create recipe that makes these items and is made by hand or on a block: the ones the book can show."""
    found = {}
    for name in create.namelist():
        if not (name.startswith('data/create/recipe/') and name.endswith('.json')):
            continue
        data = json.loads(create.read(name))
        if data.get('type') not in ('minecraft:crafting_shaped', 'minecraft:crafting_shapeless', 'create:item_application',
                                    'create:mechanical_crafting'):
            continue
        result = data.get('result') or (data.get('results') or [{}])[0]
        item = result.get('id') or result.get('item')
        if item and item.startswith('create:') and item.split(':')[1] in items:
            found.setdefault(item.split(':')[1], []).append('create:' + name[len('data/create/recipe/'):-5])
    ordered = []
    for item in items:
        recipes = sorted(found.get(item, []), key=lambda r: ('conversion' in r or 'from_' in r, r))
        ordered += recipes[:1]
    return ordered


def main(create_jar, th_jar, resources):
    create = zipfile.ZipFile(create_jar)
    th = zipfile.ZipFile(th_jar)
    for folder in ('data/%s' % NS, 'assets/%s' % NS):
        shutil.rmtree(os.path.join(resources, folder), ignore_errors=True)
    files = {}
    lang = {'en_us': {}, 'ru_ru': {}}
    known = {n.split('/')[-1][:-5] for n in create.namelist() if n.startswith('assets/create/models/item/')}
    locks = {}

    # The chapter: Artifice's background washed towards brass, Create's brass hand for an icon.
    back = Image.open(io.BytesIO(th.read('assets/thaumaturge/textures/gui/gui_research_back_4_smooth.png'))).convert('RGB')
    tint = Image.new('RGB', back.size, (150, 110, 50))
    files['assets/%s/textures/gui/research_back_mechanoturgy.png' % NS] = Image.blend(ImageEnhance.Color(back).enhance(0.4), tint, 0.25)
    files['data/%s/thaumaturge/research_category/mechanoturgy.json' % NS] = {
        'background': NS + ':textures/gui/research_back_mechanoturgy.png', 'icon': 'create:textures/item/brass_hand.png',
        'index': 7, 'overlay_background': TH + 'textures/gui/gui_research_back_over.png', 'required_research': NS + ':unlock_mechanoturgy',
        'formula': [{'aspect': TH + a, 'amount': n} for a, n in (('machina', 10), ('motus', 10), ('potentia', 10), ('metallum', 10),
                                                                 ('instrumentum', 10), ('ordo', 5), ('aqua', 5), ('aer', 5), ('ignis', 5))]}
    lang['en_us']['research_category.%s.mechanoturgy' % NS] = 'Mechanoturgy'
    lang['ru_ru']['research_category.%s.mechanoturgy' % NS] = 'Механотургия'

    # Opening the chapter: an entry in Thaumaturge's first chapter, beside the other openings, as they are.
    files['data/%s/thaumaturge/research_entry/unlock_mechanoturgy.json' % NS] = {
        'category': TH + 'basics', 'name': 'research.%s.unlock_mechanoturgy.title' % NS,
        'parents': [TH + 'unlock_artifice'], 'column': 4, 'row': 6, 'meta': ['round', 'spiky'],
        'icons': ['create:textures/item/brass_hand.png'],
        'stages': [{'text': 'research.%s.unlock_mechanoturgy.stage_0' % NS, 'required_knowledge': [obs('artifice')],
                    'required_research': [scan('motus'), scan('machina')]},
                   {'text': 'research.%s.unlock_mechanoturgy.stage_1' % NS}],
        'note_aspects': [{'aspect': TH + 'machina', 'amount': 2}, {'aspect': TH + 'motus', 'amount': 2}], 'complexity': 1}
    for code, key in (('en_us', 'en'), ('ru_ru', 'ru')):
        title, texts = UNLOCK[key]
        lang[code]['research.%s.unlock_mechanoturgy.title' % NS] = title
        for i, text in enumerate(texts):
            lang[code]['research.%s.unlock_mechanoturgy.stage_%d' % (NS, i)] = text

    for eid, e in ENTRIES.items():
        for item in e['items']:
            assert item in known or item in ('andesite_casing', 'brass_casing', 'railway_casing'), item
            locks['create:' + item] = NS + ':' + eid
        recipes = recipes_for(create, e['items'])
        stages = []
        for i, stage in enumerate(e['stages']):
            stage = dict(stage)
            stage['text'] = 'research.%s.%s.stage_%d' % (NS, eid, i)
            if i == len(e['stages']) - 1 and recipes:
                stage['recipes'] = recipes
            stages.append(stage)
        entry = {'category': CAT, 'name': 'research.%s.%s.title' % (NS, eid), 'parents': e['parents'], 'column': e['col'],
                 'row': e['row'], 'icons': [e['icon']], 'stages': stages,
                 'note_aspects': [{'aspect': TH + a, 'amount': n} for a, n in e['aspects'].items()],
                 'complexity': 1 if len(e['stages']) == 1 else 2}
        if e.get('meta'):
            entry['meta'] = e['meta']
        files['data/%s/thaumaturge/research_entry/%s.json' % (NS, eid)] = entry
        for code, key in (('en_us', 'en'), ('ru_ru', 'ru')):
            title, texts = e[key]
            assert len(texts) == len(e['stages']), eid
            lang[code]['research.%s.%s.title' % (NS, eid)] = title
            for i, text in enumerate(texts):
                lang[code]['research.%s.%s.stage_%d' % (NS, eid, i)] = text

    lang['en_us']['tooltip.%s.locked' % NS] = 'Needs the research: %s'
    lang['ru_ru']['tooltip.%s.locked' % NS] = 'Нужно исследование: %s'
    for code, values in lang.items():
        files['assets/%s/lang/%s.json' % (NS, code)] = values
    files['data/%s/research_locks.json' % NS] = dict(sorted(locks.items()))

    for path, value in files.items():
        file = os.path.join(resources, path)
        os.makedirs(os.path.dirname(file), exist_ok=True)
        if isinstance(value, Image.Image):
            value.save(file)
        else:
            with open(file, 'w', encoding='utf-8') as f:
                json.dump(value, f, indent=2, ensure_ascii=False)
                f.write('\n')
    print(len(files), 'files,', len(locks), 'locked items')


if __name__ == '__main__':
    main(*sys.argv[1:4])
