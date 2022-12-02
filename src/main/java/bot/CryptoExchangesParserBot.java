package bot;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


public class CryptoExchangesParserBot extends TelegramLongPollingBot {
    public static CryptoExchangesParserBot bot;
    public static Timer t;
    public static MyTask autoUpdateTask;
    public static final MainService mainService = MainService.getInstance();
    private static ArrayList<String> exchangeList = null;
    private static String botConfigPath = null;
    private static String botConfigName = null;
    private static String botUsername = null;
    private static String botToken = null;
    private static String botPassword = null;
    private static String pathToArbChains = null;
    private static boolean botLoggedIn = false;
    private static boolean autoUpdateRunning = false;
    private static String botChatId = null;
    public static int botAutoUpdateSeconds = 0;
    private static int topChainsCount = 0;

    private void setBotConfigPath() {
        try(InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("path_config.json")){
            JSONParser jsonParser = new JSONParser();
            assert in != null;
            org.json.simple.JSONObject data = (org.json.simple.JSONObject) jsonParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            botConfigPath = (String) data.get("bot_config_path");
            botConfigName = (String) data.get("bot_config_name");
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createBlackLists(org.json.simple.JSONObject data) {
        Map<String, Set<String>> blackLists = new HashMap<>();
        List<String> exNames = exchangeList;
//        List<String> exNames = new ArrayList<>(Arrays.asList("okx", "binance", "gate", "bybit", "huobi", "kucoin"));
        exNames.forEach(exName -> blackLists.put(exName, Arrays.stream(((String) data.get(exName + "_black_list")).split(", "))
                .collect(Collectors.toSet())));
        mainService.updateBlackLists(blackLists);
    }

    private void setBotSettings() {
        try(InputStream in = new FileInputStream(botConfigPath + botConfigName)){
            JSONParser jsonParser = new JSONParser();
            org.json.simple.JSONObject data = (org.json.simple.JSONObject) jsonParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            botUsername = (String) data.get("bot_username");
            botToken = (String) data.get("bot_token");
            botChatId = (String) data.get("bot_chat_id");
            botLoggedIn = !Boolean.parseBoolean(String.valueOf(data.get("bot_need_auth")));
            botPassword = (String) data.get("bot_password");
            exchangeList = new ArrayList<>(Arrays.stream(((String) data.get("exchange_list")).split(", ")).toList());
            mainService.setExchangeList(exchangeList);
            mainService.updateCommonBlackList(new ArrayList<>(Arrays.stream(((String) data.get("common_black_list")).split(", ")).toList()));
            createBlackLists(data);
            topChainsCount = Integer.parseInt(String.valueOf(data.get("top_arb_chains_count")));
            botAutoUpdateSeconds = Integer.parseInt(String.valueOf(data.get("bot_auto_update_seconds")));
            mainService.setArbChainProfitFilter(String.valueOf(data.get("filter_profit")));
            mainService.setLiquidityFilter(String.valueOf(data.get("filter_liquidity")));
            mainService.setQuoteAssetFilter(String.valueOf(data.get("filter_pair_to")));
            mainService.setSubListMaxDim(Integer.parseInt(String.valueOf(data.get("signal_sell_ex_count"))) + 1);
            pathToArbChains = data.get("path_to_arb_chains") + "arb_chains.txt";
        }
        catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeUsingFileWriter(ArrayList<String> dataList) {
        String data = dataList.toString().replaceAll("[\\[\\]]", "").replaceAll(",", " ");
        File file = new File(pathToArbChains);
        try (FileWriter fr = new FileWriter(file)) {
            fr.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String eraseTrailingZero(String s) {
        return s.contains(".") ? s.replaceAll("0*$","").replaceAll("\\.$","") : s;
    }

    private static String arbChainToTextSignal(ArbChain arbChain) {
        Ticker tickerFrom = arbChain.tickerFrom;
        List<Ticker> tickerTo = arbChain.tickerTo;

        StringBuilder signal = new StringBuilder("💎" + tickerFrom.pairAsset.first + "/" + tickerFrom.pairAsset.second + "\n"
                + "📉Купить на: " + tickerFrom.exName.toUpperCase() + " по цене: " + eraseTrailingZero(tickerFrom.lastPrice) + "$\n"
                + "💰Объем 24ч: " + String.format("%,.0f", Double.parseDouble(eraseTrailingZero(tickerFrom.vol24h))) + "$\n");

        for (int i = 0; i < arbChain.profit.size(); ++i) {
            String cur = " (профит: " + String.format("%,.2f", Double.parseDouble(eraseTrailingZero(arbChain.profit.get(i)))) + "%)" + "\n"
                    + "📈Продать на: " + arbChain.tickerTo.get(i).exName.toUpperCase() + " по цене: " + eraseTrailingZero(tickerTo.get(i).lastPrice) + "$\n"
                    + "💰Объем 24ч: " + String.format("%,.0f", Double.parseDouble(eraseTrailingZero(tickerTo.get(i).vol24h)) )+ "$\n";
            signal.append(cur);
        }

        return signal.toString();
    }

    public void sendYourTopArbChains() throws TelegramApiException {
        ArrayList<ArbChain> arbChains = mainService.getArbChains();
        if (arbChains == null) {
            botSendMessage("Нет данных.", botChatId);
            return;
        }
        if (arbChains.isEmpty()) {
            System.out.println("В данный момент нет доступных связок по заданным фильтрам.");
            return;
        }

        for (int i = 0; i < Math.min(arbChains.size(), topChainsCount); ++i) {
            botSendMessage(arbChainToTextSignal(arbChains.get(i)), botChatId);
        }
    }

    private void saveSettingsToConfig() {
        org.json.JSONObject config = new org.json.JSONObject();
        config.put("bot_username", botUsername);
        config.put("bot_token", botToken);
        config.put("bot_chat_id", botChatId);
        config.put("bot_need_auth", !botLoggedIn);
        config.put("bot_auto_update_seconds", botAutoUpdateSeconds);
        config.put("top_arb_chains_count", topChainsCount);
        config.put("exchange_list", exchangeList.toString().replaceAll("[\\[\\]]", ""));
        config.put("common_black_list", mainService.getCommonBlackListSet().toString().replaceAll("[\\[\\]]", ""));
        mainService.getBlackLists().forEach((key, value) -> config.put(key + "_black_list", value.toString().replaceAll("[\\[\\]]", "")));
        config.put("filter_profit", Double.parseDouble(mainService.getArbChainProfitFilter()));
        config.put("filter_liquidity", Integer.parseInt(mainService.getLiquidityFilter()));
        config.put("filter_pair_to", mainService.getQuoteAssetFilter());
        config.put("signal_sell_ex_count", mainService.getSubListMaxDim() - 1);
        config.put("path_to_arb_chains", pathToArbChains);
        config.put("bot_password", botPassword);

        File file = new File(botConfigPath + botConfigName);
        try (FileWriter fr = new FileWriter(file)) {
            fr.write(config.toString(4));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateMainInstance() throws IOException, ParseException {
        mainService.updateInstance();
    }

    public String getChatId() {
        return botChatId;
    }

    public CryptoExchangesParserBot(DefaultBotOptions options) {
        super(options);
        setBotConfigPath();
        setBotSettings();
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    private void botSendMessage(String text, String chatId) throws TelegramApiException {
        execute(SendMessage.builder()
                .text(text)
                .chatId(chatId)
                .build());
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            try {
                handleMessage(update.getMessage());
            } catch (TelegramApiException | ParseException | IOException e) {
                throw new RuntimeException(e);
            }
        } else if (update.hasCallbackQuery()) {
            try {
                handleCallback(update.getCallbackQuery());
            } catch (TelegramApiException | IOException | ParseException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) throws TelegramApiException, IOException, ParseException {
        String[] param = callbackQuery.getData().split(":");
        String action = param[0];

        switch (action) {
            case "SET_CHAT_ID" -> botSendMessage("Пришлите сообщение в формате: /set_chat_id id_чата\nПример:/set_chat_id 99999999", botChatId);
            case "SET_BL_SETTINGS" -> {
                List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
                buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                        .text("Добавить в общий черный список")
                        .callbackData("UPDATE_COMMON_BLACK_LIST:")
                        .build()
                ));
                buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                        .text("Добавить в конкретный черный список")
                        .callbackData("UPDATE_BLACK_LIST:")
                        .build()
                ));
                buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                        .text("Удалить из общего черного списка")
                        .callbackData("UPDATE_COMMON_BLACK_LIST:")
                        .build()
                ));
                buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                        .text("Удалить из конкретного черного списка")
                        .callbackData("REMOVE_BLACK_LIST:")
                        .build()
                ));

                execute(SendMessage.builder()
                        .text("Настройки черных списков:")
                        .chatId(botChatId)
                        .replyMarkup(new InlineKeyboardMarkup(buttons))
                        .build());
            }
            case "UPDATE_COMMON_BLACK_LIST" -> botSendMessage("Пришлите сообщение в формате: /update_common_black_list имя_биржи: список_через_запятую\nПример: /update_common_black_list BTC, PERP, SOL", botChatId);
            case "REMOVE_COMMON_BLACK_LIST" -> botSendMessage("Пришлите сообщение в формате: /remove_common_black_list имя_биржи: список_через_запятую\nПример: /remove_common_black_list BTC, PERP, SOL", botChatId);
            case "UPDATE_BLACK_LIST" -> botSendMessage("Пришлите сообщение в формате: /update_black_list имя_биржи: список_через_запятую\nПример: /update_black_list binance: BTC, PERP, SOL", botChatId);
            case "REMOVE_BLACK_LIST" -> botSendMessage("Пришлите сообщение в формате: /remove_black_list имя_биржи: список_через_запятую\nПример: /remove_black_list binance: BTC, PERP, SOL", botChatId);
            // botSendMessage("Пришлите сообщение в формате: \nПример:", botChatId);
            case "SET_QUOTE_FILTER" -> botSendMessage("Пришлите сообщение в формате: /set_filter_pair_to имя_тикера\nПример: /set_filter_pair_to USDT", botChatId);
            case "SET_LIQUIDITY_FILTER" -> botSendMessage("Пришлите сообщение в формате: /set_filter_liquidity целое_число\nПример: /set_filter_liquidity 1000000", botChatId);
            case "SET_PROFIT_FILTER" -> botSendMessage("Пришлите сообщение в формате: /set_profit_filter вещественное_число\nПример: /set_profit_filter 0.5", botChatId);
            case "SET_TOP_COUNT" -> botSendMessage("Пришлите сообщение в формате: /set_top_count целое_число\nПример: /set_top_count 3", botChatId);
            case "SET_SELL_EX_COUNT" -> botSendMessage("Пришлите сообщение в формате: /set_sell_ex_count целое_число\nПример: /set_sell_ex_count 2", botChatId);
            case "SET_AUTO_UPDATE" -> botSendMessage("Пришлите сообщение в формате: /set_auto_update период_в_секундах\nПример: /set_auto_update 30", botChatId);
            case "GET_COMMON_BL" -> {
                Set<String> commonBlackListSet = mainService.getCommonBlackListSet();
                botSendMessage(commonBlackListSet.toString().replaceAll("[\\[\\]]", "").replaceAll(",", " "), botChatId);
            }
            case "GET_BLS" -> {
                Map<String, Set<String>> blackLists = mainService.getBlackLists();
                blackLists.forEach((key, value) -> {
                    try {
                        botSendMessage(key + ": " + value.toString().replaceAll("[\\[\\]]", "").replaceAll(",", " "), botChatId);
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            case "PARSE_DATA" -> {
                botSendMessage("Данные обновляются...", botChatId);
                mainService.updateInstance();
                botSendMessage("Все данные были обновлены.", botChatId);
            }
            case "GET_TOP_FILTERED" -> sendYourTopArbChains();
            case "GET_ARBS_TO_FILE" -> {
                ArrayList<String> textList = new ArrayList<>();
                mainService.getArbChains().forEach(arbChain -> textList.add(arbChainToTextSignal(arbChain)));

                if (textList.isEmpty()) {
                    System.out.println("В данный момент нет доступных связок по заданным фильтрам.");
                    return;
                }
                writeUsingFileWriter(textList);
                botSendMessage("Связки записаны в файл " + pathToArbChains, botChatId);
            }
            case "GET_EXCHANGE_LIST" -> {
                ArrayList<ArbChain> arbChains = mainService.getUnfilteredArbChains();
                for (int i = 0; i < Math.min(arbChains.size(), topChainsCount); ++i) {
                    botSendMessage(arbChainToTextSignal(arbChains.get(i)), botChatId);
                }
            }
            default -> botSendMessage("Невозможно распознать команду.", botChatId);
        }
    }

    private void setMarkup(String messageText, List<String> buttonTexts) throws TelegramApiException {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(false);
        replyKeyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();
        buttonTexts.forEach(text -> {
            KeyboardRow cur = new KeyboardRow();
            cur.add(new KeyboardButton(text));
            keyboard.add(cur);
        });
        replyKeyboardMarkup.setKeyboard(keyboard);

        execute(SendMessage.builder()
                .text(messageText)
                .chatId(botChatId)
                .replyMarkup(replyKeyboardMarkup)
                .build());
    }

    private void setMenu() throws TelegramApiException {
        List<String> buttonTexts = new ArrayList<>();
        if (!autoUpdateRunning)
            buttonTexts.add("Запуск автообновления");

        buttonTexts.addAll(Arrays.asList("Действия с данными", "Задать настройки", "Обновить конфиг", "Сохранить настройки в конфиг"));
        setMarkup("Выберите действие:", buttonTexts);
    }

    private void handleMessage(Message message) throws TelegramApiException, IOException, ParseException {
        if (!message.hasText())
            return;

        if (message.hasEntities()) {
            Optional<MessageEntity> commonEntity =
                    message.getEntities().stream().filter(e -> "bot_command".equals(e.getType())).findFirst();
            if (commonEntity.isPresent()) {
                String command = message.getText().substring(commonEntity.get().getOffset(), commonEntity.get().getLength());
                ArrayList<String> textList = new ArrayList<>();
                ArrayList<ArbChain> arbChains = new ArrayList<>();
                if (!botLoggedIn && !(command.equals("/password") || command.equals("/reload_settings"))) {
                    botSendMessage("Сначала введите пароль от бота, команда /password.", botChatId);
                    return;
                }
                switch (command) {
                    case "/start" -> {
                        setMarkup("id данного чата: " + message.getChatId() + "\nВыберите действие:",
                                new ArrayList<>(Arrays.asList("Запуск автообновления", "Задать настройки", "Меню")));
                    }
                    case "/stop" -> {
                        if (t == null) {
                            botSendMessage("Автообновление не задано.", botChatId);
                        }

                        t.cancel();
                        botSendMessage("Автообновление остановлено.", botChatId);
                    }
                    case "/menu" -> {
                        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
                        buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                                .text("Посмотреть данные")
                                .callbackData("GET_DATA:")
                                .build()
                        ));
                        buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                                .text("Задать настройки")
                                .callbackData("SET_SETTINGS:")
                                .build()
                        ));
                        buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                                .text("Обновить конфиг")
                                .callbackData("UPDATE_SETTINGS:")
                                .build()
                        ));
                        buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                                .text("Сохранить настройки в конфиг")
                                .callbackData("SAVE_SETTINGS:")
                                .build()
                        ));

                        execute(SendMessage.builder()
                                .text("Выберите действие:")
                                .chatId(botChatId)
                                .replyMarkup(new InlineKeyboardMarkup(buttons))
                                .build());
                    }
                    case "/password" -> {
                        String pass = "";
                        try {
                            pass = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        } catch (Exception e) {
                            botSendMessage("Введенный пароль пуст. Попробуй снова.\nПример: /password a12345", botChatId);
                            return;
                        }

                        if (pass.isEmpty()) {
                            botSendMessage("Введенный пароль пуст. Попробуй снова.\nПример: /password a12345", botChatId);
                            return;
                        }

                        String loginResult = "";
                        if (!pass.equals(botPassword)) {
                            loginResult = "Пароль неверный.";
                        } else {
                            botLoggedIn = true;
                            loginResult = "Авторизация пройдена.";
                        }
                        botSendMessage(loginResult, botChatId);
                    }
                    case "/set_chat_id" -> {
                        String substring = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        if (substring.isEmpty()) {
                            botSendMessage("Данные в сообщении не найдены.", botChatId);
                            return;
                        }

                        botChatId = substring;
                        botSendMessage("id чата был обновлен.", botChatId);
                    }
                    case "/set_top_count" -> {
                        String substring = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        if (substring.isEmpty()) {
                            botSendMessage("Данные в сообщении не найдены.", botChatId);
                            return;
                        }

                        topChainsCount = Integer.parseInt(substring);
                        botSendMessage("Количество сигналов в топе было обновлено.", botChatId);
                    }
                    case "/set_sell_ex_count" -> {
                        String substring = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        if (substring.isEmpty()) {
                            botSendMessage("Данные в сообщении не найдены.", botChatId);
                            return;
                        }

                        mainService.setSubListMaxDim(Integer.parseInt(substring) + 1);
                        botSendMessage("Количество бирж было изменено", botChatId);
                    }
                    case "/set_blacklist_settings" -> {
                        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
                        buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                                        .text("Добавить в общий черный список")
                                        .callbackData("UPDATE_COMMON_BLACK_LIST:")
                                        .build()
                        ));
                        buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                                        .text("Добавить в конкретный черный список")
                                        .callbackData("UPDATE_BLACK_LIST:")
                                        .build()
                        ));
                        buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                                        .text("Удалить из общего черного списка")
                                        .callbackData("UPDATE_COMMON_BLACK_LIST:")
                                        .build()
                        ));
                        buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                                        .text("Удалить из конкретного черного списка")
                                        .callbackData("REMOVE_BLACK_LIST:")
                                        .build()
                        ));

                        execute(SendMessage.builder()
                                .text("Настройки черных списков:")
                                .chatId(botChatId)
                                .replyMarkup(new InlineKeyboardMarkup(buttons))
                                .build());
                    }
                    case "/update_black_list" -> {
                        String data = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        ArrayList<String> dataSeparated = new ArrayList<>(Arrays.stream(data.split(": ")).toList());
                        String exName = dataSeparated.get(0);
                        ArrayList<String> symbolsList = Arrays.stream(dataSeparated.get(1).split(", ")).collect(Collectors.toCollection(ArrayList::new));
                        if (exName.equals("common")) {
                            mainService.updateCommonBlackList(symbolsList);
                            botSendMessage("Черный список " + exName + " был обновлен.", botChatId);
                            return;
                        }
                        Set<String> symbols = new HashSet<>(symbolsList);
                        Map<String, Set<String>> blackLists = new HashMap<>();
                        blackLists.put(exName, symbols);
                        mainService.updateBlackLists(blackLists);
                        botSendMessage("Черный список " + exName + " был обновлен.", botChatId);
                    }
                    case "/remove_black_list" -> {
                        String data = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        ArrayList<String> dataSeparated = new ArrayList<>(Arrays.stream(data.split(": ")).toList());
                        String exName = dataSeparated.get(0);
                        ArrayList<String> symbolsList = Arrays.stream(dataSeparated.get(1).split(", ")).collect(Collectors.toCollection(ArrayList::new));
                        if (exName.equals("common")) {
                            mainService.removeCommonBlackList(symbolsList);
                            botSendMessage("Черный список " + exName + " был обновлен.", botChatId);
                            return;
                        }
                        Set<String> symbols = new HashSet<>(symbolsList);
                        mainService.removeBlackLists(exName, symbols);
                        botSendMessage("Черный список " + exName + " был обновлен.", botChatId);
                    }
                    case "/save_settings_to_config" -> {
                        setBotSettings();
                        saveSettingsToConfig();
                        botSendMessage("Настройки были успешно обновлены и сохранены в конфигурационный файл.", botChatId);
                    }
                    case "/set_filter_pair_to" -> {
                        String filterPairTo = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        mainService.setQuoteAssetFilter(filterPairTo);
                        botSendMessage("Фильтр по второму активу в паре задан: " + filterPairTo, botChatId);
                    }
                    case "/set_filter_liquidity" -> {
                        String filterLiquidity = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        mainService.setLiquidityFilter(filterLiquidity);
                        botSendMessage("Фильтр по объему ликвидности задан: " + filterLiquidity + "$", botChatId);
                    }
                    case "/set_allowed_asset_list" -> {
                        String substring = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        if (substring.isEmpty()) {
                            botSendMessage("Список не может быть пустым.", botChatId);
                            return;
                        }

                        ArrayList<String> allowed_list = new ArrayList<>(Arrays.stream(substring.split(", ")).toList());
                        mainService.setAllowedBaseAssetsSet(allowed_list);
                        botSendMessage("Список разрешенных базовых криптовалют установлен.", botChatId);
                    }
                    case "/set_profit_filter" -> {
                        String substring = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        if (substring.isEmpty()) {
                            botSendMessage("Данные в сообщении не найдены.", botChatId);
                            return;
                        }

                        mainService.setArbChainProfitFilter(substring);
                        botSendMessage("Фильтр по профиту обновлен.", botChatId);
                    }
                    case "/set_auto_update" -> {
                        String substring = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        if (substring.isEmpty()) {
                            botSendMessage("Пустые данные. Попробуйте еще раз.", botChatId);
                            return;
                        }

                        botAutoUpdateSeconds = Integer.parseInt(substring);
                        botSendMessage("Период автоматического обновления задан.", botChatId);

                        if (t == null)
                            return;

                        t.cancel();
                        t = new Timer();
                        autoUpdateTask = new MyTask(bot);
                        t.scheduleAtFixedRate(autoUpdateTask, 0, botAutoUpdateSeconds * 1000L);
                        botSendMessage("Автообновление перезапущено.", botChatId);
                        autoUpdateRunning = true;
                    }
                    case "/update" -> {
                        botSendMessage("Данные обновляются...", botChatId);
                        mainService.updateInstance();
                        botSendMessage("Все данные были обновлены.", botChatId);
                    }
                    case "/get_exchanges" -> {
                        botSendMessage(exchangeList.toString(), botChatId);
                    }
                    case "/get_your_file_chains" -> {
                        textList = new ArrayList<>();
                        ArrayList<String> finalTextList = textList;
                        mainService.getArbChains().forEach(arbChain -> finalTextList.add(arbChainToTextSignal(arbChain)));

                        if (textList.isEmpty()) {
                            System.out.println("В данный момент нет доступных связок по заданным фильтрам.");
                            return;
                        }
                        writeUsingFileWriter(textList);
                        botSendMessage("Связки записаны в файл " + pathToArbChains, botChatId);
                    }
                    case "/get_your_top_arb_chains" -> {
                        sendYourTopArbChains();
                    }
                    case "/get_all_top_arb_chains" -> {
                        arbChains = mainService.getUnfilteredArbChains();
                        for (int i = 0; i < Math.min(arbChains.size(), topChainsCount); ++i) {
                            botSendMessage(arbChainToTextSignal(arbChains.get(i)), botChatId);
                        }
                    }
                    case "/get_common_bl" -> {
                        Set<String> commonBlackListSet = mainService.getCommonBlackListSet();
                        botSendMessage(commonBlackListSet.toString().replaceAll("[\\[\\]]", "").replaceAll(",", " "), botChatId);
                    }
                    case "/get_all_bl" -> {
                        Map<String, Set<String>> blackLists = mainService.getBlackLists();
                        blackLists.forEach((key, value) -> {
                            try {
                                botSendMessage(key + ": " + value.toString().replaceAll("[\\[\\]]", "").replaceAll(",", " "), botChatId);
                            } catch (TelegramApiException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                    case "/reload_settings" -> {
                        setBotSettings();
                        botSendMessage("Настройки обновились.", botChatId);
                    }
                    default -> botSendMessage("Бот не может прочитать данную команду.", botChatId);
                }
            }
        } else if (botLoggedIn) {
            final String messageText = message.getText();
            switch (messageText) {
                case "Меню" -> setMenu();
                case "Запуск автообновления" -> {
                    t = new Timer();
                    autoUpdateTask = new MyTask(bot);
                    t.scheduleAtFixedRate(autoUpdateTask, 0, botAutoUpdateSeconds * 1000L);
                    botSendMessage("Автообновление запущено.", botChatId);
                    autoUpdateRunning = true;
                    setMenu();
                }
                case "Действия с данными" -> {
                    List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
                    buttons.add(Arrays.asList(
                            InlineKeyboardButton.builder()
                                    .text("Общий ЧС")
                                    .callbackData("GET_COMMON_BL:")
                                    .build(),
                            InlineKeyboardButton.builder()
                                    .text("Конкретные ЧС")
                                    .callbackData("GET_BLS:")
                                    .build()));
                    buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                            .text("Парсить данные")
                            .callbackData("PARSE_DATA:")
                            .build()
                    ));
                    buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                            .text("Топ по фильтрам")
                            .callbackData("GET_TOP_FILTERED:")
                            .build()
                    ));
                    buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                            .text("Связки по фильтру в файл")
                            .callbackData("GET_ARBS_TO_FILE:")
                            .build()
                    ));
                    buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                            .text("Список обрабатываемых бирж")
                            .callbackData("GET_EXCHANGE_LIST:")
                            .build()
                    ));

                    execute(SendMessage.builder()
                            .text("Выберите действие:")
                            .chatId(botChatId)
                            .replyMarkup(new InlineKeyboardMarkup(buttons))
                            .build());
                }
                case "Задать настройки" -> {
                    List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
                    buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                            .text("Задать id чата")
                            .callbackData("SET_CHAT_ID:")
                            .build()
                    ));
                    buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                            .text("Задать настройки ЧС")
                            .callbackData("SET_BL_SETTINGS:")
                            .build()
                    ));
                    buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                            .text("Задать фильтр по паре")
                            .callbackData("SET_QUOTE_FILTER:")
                            .build()
                    ));
                    buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                            .text("Задать фильтр по ликвидности")
                            .callbackData("SET_LIQUIDITY_FILTER:")
                            .build()
                    ));
                    buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                            .text("Задать фильтр по профиту")
                            .callbackData("SET_PROFIT_FILTER:")
                            .build()
                    ));
                    buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                            .text("Задать количество сигналов в топе")
                            .callbackData("SET_TOP_COUNT:")
                            .build()
                    ));
                    buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                            .text("Задать количество бирж, на которых пара продается")
                            .callbackData("SET_SELL_EX_COUNT:")
                            .build()
                    ));
                    buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                            .text("Задать период автоапдейта")
                            .callbackData("SET_AUTO_UPDATE:")
                            .build()
                    ));

                    execute(SendMessage.builder()
                            .text("Выберите действие:")
                            .chatId(botChatId)
                            .replyMarkup(new InlineKeyboardMarkup(buttons))
                            .build());
                }
                case "Обновить настройки" -> {
                    setBotSettings();
                    botSendMessage("Настройки обновились.", botChatId);
                }
                case "Сохранить настройки" -> {
                    setBotSettings();
                    saveSettingsToConfig();
                    botSendMessage("Настройки были успешно обновлены и сохранены в конфигурационный файл.", botChatId);
                }
                default -> botSendMessage("Бот не может распознать данное сообщение.", botChatId);
            }
        } else {
            botSendMessage("Сначала введите пароль от бота, команда /password.", botChatId);
        }
    }

    private static class MyTask extends TimerTask {
        private static CryptoExchangesParserBot TaskBot;

        public MyTask(CryptoExchangesParserBot newBot) {
            MyTask.TaskBot = newBot;
        }

        @Override
        public void run() {
            try {
                TaskBot.updateMainInstance();
                TaskBot.sendYourTopArbChains();
            } catch (TelegramApiException | IOException | ParseException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) throws TelegramApiException {
        bot = new CryptoExchangesParserBot(new DefaultBotOptions());
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(bot);

//        String botChatId = bot.getChatId();
//        bot.execute(SendMessage.builder().chatId(botChatId).text("Бот запущен.").build());
//        t = new Timer();
//        autoUpdateTask = new MyTask(bot);
//        t.scheduleAtFixedRate(autoUpdateTask, 0, botAutoUpdateSeconds * 1000L);
    }
}
