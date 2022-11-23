package bot;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


public class CryptoExchangesParserBot extends TelegramLongPollingBot {
    public static final MainService mainService = MainService.getInstance();
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
            JSONObject data = (JSONObject) jsonParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            botConfigPath = (String) data.get("bot_config_path");
            botConfigName = (String) data.get("bot_config_name");
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void createBlackLists(JSONObject data) {
        Map<String, Set<String>> blackLists = new HashMap<>();
        List<String> exNames = new ArrayList<>(Arrays.asList("okx", "binance", "gate", "bybit", "huobi", "kucoin"));
        exNames.forEach(exName -> blackLists.put(exName, Arrays.stream(((String) data.get(exName + "_black_list")).split(", "))
                .collect(Collectors.toSet())));
        mainService.setBlackLists(blackLists);
    }

    private void setBotSettings() {
        try(InputStream in = new FileInputStream(botConfigPath + botConfigName)){
            JSONParser jsonParser = new JSONParser();
            JSONObject data = (JSONObject) jsonParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            botUsername = (String) data.get("bot_username");
            botToken = (String) data.get("bot_token");
            botChatId = (String) data.get("bot_chat_id");
            botLoggedIn = !Boolean.parseBoolean(String.valueOf(data.get("bot_need_auth")));
            botPassword = (String) data.get("bot_password");
            mainService.setCommonBlackList(new ArrayList<>(Arrays.stream(((String) data.get("common_black_list")).split(", ")).toList()));
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

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            try {
                handleMessage(update.getMessage());
            } catch (TelegramApiException | ParseException | IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void botSendMessage(String text, String chatId) throws TelegramApiException {
        execute(SendMessage.builder()
                .text(text)
                .chatId(chatId)
                .build());
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

    public void updateMainInstance() throws IOException, ParseException {
        mainService.updateInstance();
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
                    case "/update" -> {
                        botSendMessage("Данные обновляются...", botChatId);
                        mainService.updateInstance();
                        botSendMessage("Все данные были обновлены.", botChatId);
                    }
                    case "/get_all_symbols" -> {
                        botSendMessage("Все доступные пары: " + mainService.getCommonSymbols().toString(), botChatId);
                    }
                    case "/get_your_list_profit_message" -> {
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
        private static CryptoExchangesParserBot bot;

        public MyTask(CryptoExchangesParserBot bot) {
            MyTask.bot = bot;
        }

        @Override
        public void run() {
            try {
                bot.updateMainInstance();
                bot.sendYourTopArbChains();
//                System.out.println(botAutoUpdateSeconds);
//                bot.execute(SendMessage.builder().chatId(botChatId).text("Текущий фильтр ликвидности: " + mainService.getLiquidityFilter()).build());
            } catch (TelegramApiException | IOException | ParseException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public static void main(String[] args) throws TelegramApiException {
        CryptoExchangesParserBot bot = new CryptoExchangesParserBot(new DefaultBotOptions());
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(bot);
        String botChatId = bot.getChatId();
        bot.execute(SendMessage.builder().chatId(botChatId).text("Бот запущен.").build());
        Timer t = new Timer();
        MyTask mTask = new MyTask(bot);
        t.scheduleAtFixedRate(mTask, 0, botAutoUpdateSeconds * 1000L);
    }
}
