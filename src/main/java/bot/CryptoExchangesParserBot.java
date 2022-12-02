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
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) throws TelegramApiException {
        String[] param = callbackQuery.getData().split(":");
        String action = param[0];

        switch (action) {
            case "BOT_RUN" -> {
                t = new Timer();
                autoUpdateTask = new MyTask(bot);
                t.scheduleAtFixedRate(autoUpdateTask, 0, botAutoUpdateSeconds * 1000L);
                botSendMessage("Автообновление запущено.", botChatId);
            }
            case "SET_SETTINGS" -> {
                System.out.println();
            }
            case "GET_DATA" -> {
                ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
                replyKeyboardMarkup.setSelective(true);
                replyKeyboardMarkup.setResizeKeyboard(true);
                replyKeyboardMarkup.setOneTimeKeyboard(false);

                List<KeyboardRow> keyboard = new ArrayList<>();

                KeyboardRow row1 = new KeyboardRow();
                KeyboardRow row2 = new KeyboardRow();
                KeyboardRow row3 = new KeyboardRow();
                row1.add(new KeyboardButton("Получить предсказание"));
                row2.add(new KeyboardButton("Моя анкета"));
                row3.add(new KeyboardButton("Помощь"));
                keyboard.add(row1);
                keyboard.add(row2);
                keyboard.add(row3);
                replyKeyboardMarkup.setKeyboard(keyboard);

                execute(SendMessage.builder()
                        .text("Выберите действие:")
                        .chatId(botChatId)
                        .replyMarkup(replyKeyboardMarkup)
                        .build());
            }
            case "UPDATE_COMMON_BLACK_LIST" -> botSendMessage("Пришлите сообщение в формате:/update_common_black_list имя_биржи: список_через_запятую\nПример: /update_common_black_list BTC, PERP, SOL", botChatId);
            case "REMOVE_COMMON_BLACK_LIST" -> botSendMessage("Пришлите сообщение в формате:/remove_common_black_list имя_биржи: список_через_запятую\nПример: /remove_common_black_list BTC, PERP, SOL", botChatId);
            case "UPDATE_BLACK_LIST" -> botSendMessage("Пришлите сообщение в формате:/update_black_list имя_биржи: список_через_запятую\nПример: /update_black_list binance: BTC, PERP, SOL", botChatId);
            case "REMOVE_BLACK_LIST" -> botSendMessage("Пришлите сообщение в формате:/remove_black_list имя_биржи: список_через_запятую\nПример: /remove_black_list binance: BTC, PERP, SOL", botChatId);
        }
    }


    private void handleMessage(Message message) throws TelegramApiException, IOException, ParseException {
        if (message.hasText() && message.hasEntities()) {
            Optional<MessageEntity> commonEntity =
                    message.getEntities().stream().filter(e -> "bot_command".equals(e.getType())).findFirst();
            if (commonEntity.isPresent()) {
                String command = message.getText().substring(commonEntity.get().getOffset(), commonEntity.get().getLength());
                ArrayList<String> textList = new ArrayList<>();
                ArrayList<ArbChain> arbChains = new ArrayList<>();
                if (!botLoggedIn && !(command.equals("/password") || command.equals("/reload_settings"))) {
                    botSendMessage("Сначала введите пароль от бота.", botChatId);
                    return;
                }
                switch (command) {
                    case "/start" -> {
                        final Long chatId = message.getChatId();
                        botChatId = String.valueOf(chatId);
                        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
                        buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                                .text("Запуск автообновления")
                                .callbackData("BOT_RUN:")
                                .build()
                        ));
                        buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                                .text("Задать настройки")
                                .callbackData("SET_SETTINGS:")
                                .build()
                        ));

                        execute(SendMessage.builder()
                                .text("id чата: " + chatId + ". Параметры старта:")
                                .chatId(botChatId)
                                .replyMarkup(new InlineKeyboardMarkup(buttons))
                                .build());
                    }
                    case "/stop" -> {
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
                                .text("Обновить настройки")
                                .callbackData("UPDATE_SETTINGS:")
                                .build()
                        ));
                        buttons.add(Collections.singletonList(InlineKeyboardButton.builder()
                                .text("Сохранить настройки")
                                .callbackData("SAVE_SETTINGS:")
                                .build()
                        ));
                        //EditMessageReplyMarkup

                        execute(SendMessage.builder()
                                .text("Выберите действие:")
                                .chatId(botChatId)
                                .replyMarkup(new InlineKeyboardMarkup(buttons))
                                .build());
                    }
                    case "/password" -> {
                        String pass = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        String loginResult = "";
                        if (!pass.equals(botPassword)) {
                            loginResult = "Пароль неверный.";
                        } else {
                            botLoggedIn = true;
                            loginResult = "Авторизация пройдена.";
                        }
                        botSendMessage(loginResult, botChatId);
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
                        botSendMessage("Настройки были успешно сохранены в конфигурационный файл.", botChatId);
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
                    case "/set_auto_update" -> {
                        String substring = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        if (substring.isEmpty()) {
                            botSendMessage("Пустые данные. Попробуйте еще раз.", botChatId);
                            return;
                        }

                        botAutoUpdateSeconds = Integer.parseInt(substring);
                        t.cancel();
                        t = new Timer();
                        autoUpdateTask = new MyTask(bot);
                        t.scheduleAtFixedRate(autoUpdateTask, 0, botAutoUpdateSeconds * 1000L);
                        botSendMessage("Период автоматического обновления задан.", botChatId);
                    }
                    case "/update" -> {
                        botSendMessage("Данные обновляются...", botChatId);
                        mainService.updateInstance();
                        botSendMessage("Все данные были обновлены.", botChatId);
                    }
                    case "/get_exchanges" -> {
                        botSendMessage(exchangeList.toString(), botChatId);
                    }
                    case "/get_all_symbols" -> {
                        botSendMessage("Все доступные пары: " + mainService.getCommonSymbols().toString(), botChatId);
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
                    case "/reload_settings" -> {
                        setBotSettings();
                        botSendMessage("Настройки обновились.", botChatId);
                    }
                    default -> botSendMessage("Бот не может прочитать данную команду.", botChatId);
                }
            }
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
