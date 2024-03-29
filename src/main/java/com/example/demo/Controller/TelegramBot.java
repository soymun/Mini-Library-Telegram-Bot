package com.example.demo.Controller;


import com.example.demo.Config.BotConfig;
import com.example.demo.Entity.Books;
import com.example.demo.Service.Imp.BookService;
import com.example.demo.Service.Imp.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {


    @Autowired
    private UserService userService;

    @Autowired
    private BookService bookService;

    final BotConfig botConfig;

    public TelegramBot(BotConfig botConfig) {
        this.botConfig = botConfig;
        List<BotCommand> listofCommands = new ArrayList<>();
        listofCommands.add(new BotCommand("/start", "get a welcome message"));
        listofCommands.add(new BotCommand("/mybook", "get your book"));
        listofCommands.add(new BotCommand("/help", "info how to use this bot"));
        try {
            this.execute(new SetMyCommands(listofCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's command list: " + e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if(update.hasMessage() && update.getMessage().hasText()){
            List<String> list = getReqest(update.getMessage().getText());
            String data = list.get(0).trim();
                switch (data){
                    case "/start" ->{
                        try {
                            userService.saveUser(update.getMessage());
                            register(update.getMessage().getChatId());
                        }
                        catch (RuntimeException e){
                            try {
                                execute(new SendMessage(update.getMessage().getChatId().toString(), e.getMessage()));
                            } catch (TelegramApiException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }
                    case "/getbooks" ->{
                        Pair<Integer, String> str = bookService.getBooks(list, 1);
                        getBook(str.getSecond(), update.getMessage().getChatId(), str.getFirst());
                    }
                    case "/getbook" ->{
                        try {
                            Pair<String, String> path = bookService.getBook(list, true);
                            InputFile fileInputStream = new InputFile(new File(path.getSecond()));
                            SendDocument sendDocument = new SendDocument();
                            sendDocument.setChatId(update.getMessage().getChatId().toString());
                            sendDocument.setDocument(fileInputStream);
                            sendDocument.setCaption(path.getFirst());
                            execute(sendDocument);
                            userService.personBooks(update.getMessage().getChatId(), Long.valueOf(list.get(1).split("=")[1]));
                        } catch (Exception e){
                            SendMessage sendMessage = new SendMessage();
                            sendMessage.setText("Не удалось загрузить файл");
                            sendMessage.setChatId(update.getMessage().getChatId().toString());
                            executeMessage(sendMessage);
                        }
                    }
                    case "/mybook" -> {
                        Pair<Integer, String> str = bookService.myBook(update.getMessage().getChatId(), 1);
                        getBook(str.getSecond(), update.getMessage().getChatId(), str.getFirst());
                    }
                    case "/checklist" -> {
                        if(userService.isAdmin(update.getMessage().getChatId())){
                            Pair<Integer, String> str = bookService.checkList(update.getMessage().getChatId(), 1);
                            getBook(str.getSecond(), update.getMessage().getChatId(), str.getFirst());
                        }
                    }
                    case "/check" -> {
                        try {
                            Pair<String, String> path = bookService.getBook(list, false);
                            InputFile fileInputStream = new InputFile(new File(path.getSecond()));
                            SendDocument sendDocument = new SendDocument();
                            sendDocument.setChatId(update.getMessage().getChatId().toString());
                            sendDocument.setDocument(fileInputStream);
                            sendDocument.setCaption(path.getFirst());
                            execute(sendDocument);
                            checkedBook(update.getMessage().getChatId(), Long.valueOf(list.get(1).split("=")[1]));
                        } catch (Exception e){
                            SendMessage sendMessage = new SendMessage();
                            sendMessage.setText("Не удалось загрузить файл");
                            sendMessage.setChatId(update.getMessage().getChatId().toString());
                            executeMessage(sendMessage);
                        }
                    }
                    case "/help" -> {
                        SendMessage sendMessage = new SendMessage();
                        if(userService.isAdmin(update.getMessage().getChatId())){
                            String text = """
                                    Команды:
                                    1)/getbooks a=Фамилия автора(или название органицазии) n=Название книги g=жанр, поиск книг
                                    2)/getbook id=Id книги, выдача книги
                                    3)/mybook, книги которые вы скачали
                                    4)/checklist, книги на проверке
                                    5)/check id=Id книги, установить результат проверки книги""";
                            sendMessage.setText(text);
                            sendMessage.setChatId(update.getMessage().getChatId().toString());
                            executeMessage(sendMessage);
                        }
                        else {
                            String text = """
                                    Команды:
                                    1)/getbooks a=Фамилия автора(или название органицазии) n=Название книги g=жанр, поиск книг
                                    2)/getbook id=Id книги, выдача книги
                                    3)/mybook, книги которые вы скачали""";
                            sendMessage.setText(text);
                            sendMessage.setChatId(update.getMessage().getChatId().toString());
                            executeMessage(sendMessage);
                        }
                    }
            }
        }
        else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if(callbackData.equals("YES_BUTTON")){
                String text = "Приветствуем в нашем боте.\nЧтобы узнать все возможности /help";
                userService.acceptRegister(update.getCallbackQuery().getMessage().getChatId());
                executeEditMessageText(text, chatId, messageId);
            }
            else if(callbackData.equals("NO_BUTTON")){
                String text = "Sorry you can't find books(";
                executeEditMessageText(text, chatId, messageId);
            }
            else if(Character.isDigit(callbackData.charAt(0))){
                int page = Integer.parseInt(callbackData);
                if (update.getCallbackQuery().getMessage().getText().startsWith("Ваш запрос")) {
                    String str = update.getCallbackQuery().getMessage().getText().split("\n")[0];
                    List<String> list = getReqest(str);
                    Pair<Integer, String> text = bookService.getBooks(list, page);
                    executeEditMessageTextBooksGet(text.getSecond(), update.getCallbackQuery().getMessage().getChatId(), update.getCallbackQuery().getMessage().getMessageId(), page, text.getFirst());
                }
                else if(update.getCallbackQuery().getMessage().getText().startsWith("Мои книги")){
                    Pair<Integer, String> text = bookService.myBook(update.getCallbackQuery().getMessage().getChatId(), page);
                    executeEditMessageTextBooksGet(text.getSecond(), update.getCallbackQuery().getMessage().getChatId(), update.getCallbackQuery().getMessage().getMessageId(), page, text.getFirst());

                }
                else if(update.getCallbackQuery().getMessage().getText().startsWith("Список книг")){
                    Pair<Integer, String> text = bookService.checkList(update.getCallbackQuery().getMessage().getChatId(), page);
                    executeEditMessageTextBooksGet(text.getSecond(), update.getCallbackQuery().getMessage().getChatId(), update.getCallbackQuery().getMessage().getMessageId(), page, text.getFirst());
                }
            }
            else if(callbackData.contains("YES_")){
                Long bookId = Long.valueOf(callbackData.split("_")[1]);
                bookService.afterCheck(bookId, true);
                String text = "Книга принята";
                executeEditMessageText(text, chatId, messageId);
            }
            else if(callbackData.contains("NO_")){
                Long bookId = Long.valueOf(callbackData.split("_")[1]);
                bookService.afterCheck(bookId, false);
                String text = "Книга удалена";
                executeEditMessageText(text, chatId, messageId);
            }
        }
        else if(update.hasMessage() && update.getMessage().hasDocument()){
            String str = update.getMessage().getCaption();
            if (str.contains("/save")) {
                List<String> list = getReqest(str);
                if(list.size() != 4){
                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setChatId(update.getMessage().getChatId().toString());
                    sendMessage.setText("Вы не указали один из параметров.\nПопробуйте снова!");
                    executeMessage(sendMessage);
                    return;
                }
                Books books = bookService.saveBook(list);
                GetFile getFileRequest = new GetFile();

                getFileRequest.setFileId(update.getMessage().getDocument().getFileId());

                org.telegram.telegrambots.meta.api.objects.File telegramFile;
                try {
                    telegramFile = execute(getFileRequest);
                    File file = downloadFile(telegramFile);
                    if(telegramFile.getFilePath().split("\\.")[1].equals("pdf")) {
                        File file1 = new File("src/main/resources/books/" + books.getId() + ".pdf");
                        if (file.renameTo(file1)) {
                            if (file.createNewFile()) {
                                books.setUrlToFile(file1.getPath());
                                bookService.updateBook(books);
                                SendMessage sendMessage = new SendMessage();
                                sendMessage.setChatId(update.getMessage().getChatId().toString());
                                sendMessage.setText("Сохранение книги прошло успешно. \nОна находится на просмотре администратора");
                                executeMessage(sendMessage);
                            }
                        }
                    }
                } catch (TelegramApiException | IOException e) {
                    throw new RuntimeException(e);
                }
            }

        }
    }

    private void executeEditMessageTextBooksGet(String text, long chatId, long messageId, int page, int count){
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setMessageId((int) messageId);
        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        int c = 0;
        for (int i = page - 3 > 0? page - 3 + 1 : 1; i*5 < count+5; i++){
            if(c <=3) {
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(Integer.toString(i));
                button.setCallbackData(Integer.toString(i));
                rowInLine.add(button);
                c++;
            }
            else {
                break;
            }
        }
        rowsInLine.add(rowInLine);
        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("ERROR_TEXT" + e.getMessage());
        }
    }

    private void getBook(String text, Long chatId, int count){
        SendMessage message = new SendMessage();
        message.setText(text);
        message.setChatId(chatId.toString());
        if(count <= 5){
            executeMessage(message);
            return;
        }
        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        int c = 0;
        for (int i = 1; i*5 < count+5; i++){
            if(c <=3) {
                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(Integer.toString(i));
                button.setCallbackData(Integer.toString(i));
                rowInLine.add(button);
                c++;
            }
            else {
                break;
            }
        }
        rowsInLine.add(rowInLine);
        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);
        executeMessage(message);
    }
    private void register(long chatId) {

        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Do you really want to register?");

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();

        yesButton.setText("Yes");
        yesButton.setCallbackData("YES_BUTTON");

        var noButton = new InlineKeyboardButton();

        noButton.setText("No");
        noButton.setCallbackData("NO_BUTTON");

        rowInLine.add(yesButton);
        rowInLine.add(noButton);

        rowsInLine.add(rowInLine);

        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);

        executeMessage(message);
    }

    public void checkedBook(long chatId, Long bookId){
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Book is correct?");

        InlineKeyboardMarkup markupInLine = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInLine = new ArrayList<>();
        List<InlineKeyboardButton> rowInLine = new ArrayList<>();
        var yesButton = new InlineKeyboardButton();

        yesButton.setText("Yes");
        yesButton.setCallbackData("YES_" + bookId);

        var noButton = new InlineKeyboardButton();

        noButton.setText("No");
        noButton.setCallbackData("NO_" + bookId);

        rowInLine.add(yesButton);
        rowInLine.add(noButton);

        rowsInLine.add(rowInLine);

        markupInLine.setKeyboard(rowsInLine);
        message.setReplyMarkup(markupInLine);

        executeMessage(message);
    }

    private void executeMessage(SendMessage message){
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("ERROR" + e.getMessage());
        }
    }

    private void executeEditMessageText(String text, long chatId, long messageId){
        EditMessageText message = new EditMessageText();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setMessageId((int) messageId);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("ERROR_TEXT" + e.getMessage());
        }
    }

    public List<String> getReqest(String str){
        List<String> list = new ArrayList<>();
        int start = 0;
        for (int i = 0; i< str.length(); i++){
            if(str.charAt(i) == '='){
                list.add(str.substring(start, i-2));
                start = i-1;
            }
        }
        list.add(str.substring(start));
        return list;
    }

}
