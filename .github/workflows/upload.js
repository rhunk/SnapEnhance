const TelegramBot = require('node-telegram-bot-api');
const program = require('commander');

program
  .option('-t, --token <token>', 'Telegram bot token')
  .option('-f, --file <filePath>', 'File path of the file to send')
  .option('-c, --caption <caption>', 'Caption for the file')
  .option('--chatid <chatId>', 'Chat ID to send the file to')
  .parse(process.argv);

const { token, chatid, file, caption } = program.opts();
const bot = new TelegramBot(token);

bot.sendDocument(chatid, file, { caption }).then(() => {
    process.exit();
})
