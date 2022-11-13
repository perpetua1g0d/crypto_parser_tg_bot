import com.sun.tools.javac.Main;
import org.json.simple.parser.ParseException;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.MessageEntity;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;


public class CryptoExchangesParserBot extends TelegramLongPollingBot {
    private final MainService mainService = MainService.getInstance();
    private final String password = "Adkllu49.z1";
    private boolean loggedIn = true;

    public CryptoExchangesParserBot(DefaultBotOptions options) throws IOException, ParseException {
        super(options);
    }

    @Override
    public String getBotUsername() {
        return "@CryptoExchangesParserBot";
    }

    @Override
    public String getBotToken() {
        return "5776713944:AAHndXSbBCk-_G4OvJd_gKPPaun6DfH7g60";
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

    private void handleMessage(Message message) throws TelegramApiException, IOException, ParseException {
        if (message.hasText() && message.hasEntities()) {
            Optional<MessageEntity> commonEntity =
                    message.getEntities().stream().filter(e -> "bot_command".equals(e.getType())).findFirst();
            if (commonEntity.isPresent()) {
                String command = message.getText().substring(commonEntity.get().getOffset(), commonEntity.get().getLength());
                switch (command) {
                    case "/password":
                        String pass = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        String loginResult = "";
                        if (!pass.equals(this.password)) {
                            loginResult = "Пароль неверный.";
                        } else {
                            loggedIn = true;
                            loginResult = "Авторизация пройдена.";
                        }
                        execute(SendMessage.builder()
                                .text(loginResult)
                                .chatId(message.getChatId().toString())
                                .build());
                        return;
                    case "/set_pair_to":
                        if (!loggedIn) {
                            execute(SendMessage.builder()
                                    .text("Сначала введите пароль от бота.")
                                    .chatId(message.getChatId().toString())
                                    .build());
                            return;
                        }
                        String filterPairTo = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        mainService.setQuoteAssetFilter(filterPairTo);
                        execute(SendMessage.builder()
                                .text("Фильтр по второму активу в паре задан: " + filterPairTo)
                                .chatId(message.getChatId().toString())
                                .build());
                        return;
                    case "/set_filter_liquidity":
                        if (!loggedIn) {
                            execute(SendMessage.builder()
                                    .text("Сначала введите пароль от бота.")
                                    .chatId(message.getChatId().toString())
                                    .build());
                            return;
                        }
                        String filterLiquidity = message.getText().substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1);
                        mainService.setLiquidityFilter(filterLiquidity);
                        execute(SendMessage.builder()
                                .text("Фильтр по объему ликвидности задан: " + filterLiquidity + "$")
                                .chatId(message.getChatId().toString())
                                .build());
                        return;
                    case "/set_allowed_asset_list":
                        if (!loggedIn) {
                            execute(SendMessage.builder()
                                    .text("Сначала введите пароль от бота.")
                                    .chatId(message.getChatId().toString())
                                    .build());
                            return;
                        }
                        ArrayList<String> allowed_list = (ArrayList<String>) Arrays.stream(message.getText()
                                .substring(commonEntity.get().getOffset() + commonEntity.get().getLength() + 1)
                                .split(", ")).toList();
                        mainService.setAllowedBaseAssetsSet(allowed_list);
                        execute(SendMessage.builder()
                                .text("Список разрешенных базовых криптовалют установлен.")
                                .chatId(message.getChatId().toString())
                                .build());
                        return;
                    case "/update":
                        if (!loggedIn) {
                            execute(SendMessage.builder()
                                    .text("Сначала введите пароль от бота.")
                                    .chatId(message.getChatId().toString())
                                    .build());
                            return;
                        }
                        mainService.updateInstance();
                        execute(SendMessage.builder()
                                .text("Все данные были обновлены.")
                                .chatId(message.getChatId().toString())
                                .build());
                        return;
                    case "/get_all_symbols":
                        execute(SendMessage.builder()
                                .text("Все доступные пары: " + mainService.getCommonSymbols().toString())
                                .chatId(message.getChatId().toString())
                                .build());
                        return;
                    case "/get_your_list_profit_message":
                        execute(SendMessage.builder()
                                .text("Связки: " + mainService.getArbChains().toString())
                                .chatId(message.getChatId().toString())
                                .build());
                    case "/get_your_top10_arb_chains":
                        execute(SendMessage.builder()
                                .text("Топ10 связок: " + mainService.getArbChains().stream().limit(10))
                                .chatId(message.getChatId().toString())
                                .build());
                    case "/get_all_top10_arb_chains":
                        execute(SendMessage.builder()
                                .text("Топ10 из всех возможных связок: " + mainService.getUnfilteredArbChains().stream().limit(10).toList().toString())
                                .chatId(message.getChatId().toString())
                                .build());
                    default:
                        execute(SendMessage.builder()
                                .text("Бот не может прочитать данную команду.")
                                .chatId(message.getChatId().toString())
                                .build());
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
