import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;


public class CryptoExchangesParserBot extends DefaultAbsSender {
    public CryptoExchangesParserBot(DefaultBotOptions options) {
        super(options);
    }

    @Override
    public String getBotToken() {
        return "5776713944:AAHndXSbBCk-_G4OvJd_gKPPaun6DfH7g60";
    }

    public static void main(String[] args) throws TelegramApiException {
        CryptoExchangesParserBot bot = new CryptoExchangesParserBot(new DefaultBotOptions());
        bot.execute(SendMessage.builder().chatId("638273225").text("Hello test message").build());
    }
}
