import org.checkerframework.checker.units.qual.A;
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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;


public class CryptoExchangesParserBot extends TelegramLongPollingBot {
    private static final MainService mainService = MainService.getInstance();
    private static boolean loggedIn = false;
    private static String botPassword = null;
    private static String botUsername = null;
    private static String botToken = null;
    private static String pathToArbChains = null;

    private void setBotSettings() {
        try(InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("bot_config.json")){
            JSONParser jsonParser = new JSONParser();
            assert in != null;
            JSONObject data = (JSONObject) jsonParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
            botUsername = (String) data.get("bot_username");
            botToken = (String) data.get("bot_token");
            loggedIn = !Boolean.parseBoolean((String) data.get("bot_need_auth"));
            botPassword = (String) data.get("bot_password");
            pathToArbChains = (String) data.get("path_to_arb_chains") + "arb_chains.txt";
        }
        catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public CryptoExchangesParserBot(DefaultBotOptions options) {
        super(options);
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

    private static String myFormatDouble(String s, DecimalFormat formatter) {
//        return formatter.format(Double.parseDouble(eraseTrailingZero(s)));
        return eraseTrailingZero(s);
    }

    private static String arbChainToTextSignal(ArbChain arbChain) {
        Ticker tickerFrom = arbChain.tickerFrom;
        Ticker tickerTo = arbChain.tickerTo;

        DecimalFormat formatter = new DecimalFormat("#,###.00");
        return "💎" + tickerFrom.pairAsset.first + "/" + tickerFrom.pairAsset.second
                + " (профит: " + String.format("%,.2f", Double.parseDouble(myFormatDouble(arbChain.profit, formatter))) + "%)" + "\n"
                + "📉Купить на: " + arbChain.exFrom + " по цене: " + myFormatDouble(tickerFrom.lastPrice, formatter) + "$\n"
                + "💰Объем 24ч: " + String.format("%,.0f", Double.parseDouble(myFormatDouble(tickerFrom.vol24h, formatter))) + "$\n"
                + "📈Продать на: " + arbChain.exTo + " по цене: " + myFormatDouble(tickerTo.lastPrice, formatter) + "$\n"
                + "💰Объем 24ч: " + String.format("%,.0f", Double.parseDouble(myFormatDouble(tickerTo.vol24h, formatter)) )+ "$\n";
    }

    private void handleMessage(Message message) throws TelegramApiException, IOException, ParseException {
        if (message.hasText() && message.hasEntities()) {
            Optional<MessageEntity> commonEntity =
                    message.getEntities().stream().filter(e -> "bot_command".equals(e.getType())).findFirst();
            if (commonEntity.isPresent()) {
                String command = message.getText().substring(commonEntity.get().getOffset(), commonEntity.get().getLength());
                ArrayList<String> textList = new ArrayList<>();
                ArrayList<ArbChain> arbChains = new ArrayList<>();
                switch (command) {
                    case "/password" -> {
                        String pass = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        String loginResult = "";
                        if (!pass.equals(botPassword)) {
                            loginResult = "Пароль неверный.";
                        } else {
                            loggedIn = true;
                            loginResult = "Авторизация пройдена.";
                        }
                        botSendMessage(loginResult, message.getChatId().toString());
                    }
                    case "/set_filter_pair_to" -> {
                        if (!loggedIn) {
                            botSendMessage("Сначала введите пароль от бота.", message.getChatId().toString());
                            return;
                        }
                        String filterPairTo = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        mainService.setQuoteAssetFilter(filterPairTo);
                        botSendMessage("Фильтр по второму активу в паре задан: " + filterPairTo, message.getChatId().toString());
                    }
                    case "/set_filter_liquidity" -> {
                        if (!loggedIn) {
                            botSendMessage("Сначала введите пароль от бота.", message.getChatId().toString());
                            return;
                        }
                        String filterLiquidity = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        mainService.setLiquidityFilter(filterLiquidity);
                        botSendMessage("Фильтр по объему ликвидности задан: " + filterLiquidity + "$", message.getChatId().toString());
                    }
                    case "/set_allowed_asset_list" -> {
                        if (!loggedIn) {
                            botSendMessage("Сначала введите пароль от бота.", message.getChatId().toString());
                            return;
                        }
                        String substring = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        if (substring.isEmpty()) {
                            botSendMessage("Список не может быть пустым.", message.getChatId().toString());
                            return;
                        }

                        ArrayList<String> allowed_list = new ArrayList<>(Arrays.stream(substring.split(", ")).toList());
//                        ArrayList<String> allowed_list = (ArrayList<String>) Arrays.stream(substring.split(",")).toList();
                        mainService.setAllowedBaseAssetsSet(allowed_list);
                        botSendMessage("Список разрешенных базовых криптовалют установлен.", message.getChatId().toString());
                    }
                    case "/update" -> {
                        if (!loggedIn) {
                            botSendMessage("Сначала введите пароль от бота.", message.getChatId().toString());
                            return;
                        }
                        botSendMessage("Данные обновляются...", message.getChatId().toString());
                        mainService.updateInstance();
                        botSendMessage("Все данные были обновлены.", message.getChatId().toString());
                    }
                    case "/get_all_symbols" -> {
                        if (!loggedIn) {
                            botSendMessage("Сначала введите пароль от бота.", message.getChatId().toString());
                            return;
                        }
                        botSendMessage("Все доступные пары: " + mainService.getCommonSymbols().toString(), message.getChatId().toString());
                    }
                    case "/get_your_list_profit_message" -> {
                        if (!loggedIn) {
                            botSendMessage("Сначала введите пароль от бота.", message.getChatId().toString());
                            return;
                        }
                        textList = new ArrayList<>();
                        ArrayList<String> finalTextList = textList;
                        mainService.getArbChains().forEach(arbChain -> finalTextList.add(arbChainToTextSignal(arbChain)));
                        writeUsingFileWriter(textList);
                        botSendMessage("Связки записаны в файл " + pathToArbChains, message.getChatId().toString());
                    }
                    case "/get_your_top10_arb_chains" -> {
                        if (!loggedIn) {
                            botSendMessage("Сначала введите пароль от бота.", message.getChatId().toString());
                            return;
                        }
                        arbChains = mainService.getArbChains();
                        for (int i = 0; i < Math.min(arbChains.size(), 10); ++i) {
                            botSendMessage(arbChainToTextSignal(arbChains.get(i)), message.getChatId().toString());
                        }
                    }
                    case "/get_all_top10_arb_chains" -> {
                        if (!loggedIn) {
                            botSendMessage("Сначала введите пароль от бота.", message.getChatId().toString());
                            return;
                        }
                        arbChains = mainService.getUnfilteredArbChains();
                        for (int i = 0; i < Math.min(arbChains.size(), 10); ++i) {
                            botSendMessage(arbChainToTextSignal(arbChains.get(i)), message.getChatId().toString());
                        }
                    }
                    default -> botSendMessage("Бот не может прочитать данную команду.", message.getChatId().toString());
                }
            }
        }
    }

    public static void main(String[] args) throws TelegramApiException, IOException, ParseException {
        CryptoExchangesParserBot bot = new CryptoExchangesParserBot(new DefaultBotOptions());
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(bot);
//        bot.execute(SendMessage.builder().chatId("638273225").text("Hello test message").build());
    }
}
