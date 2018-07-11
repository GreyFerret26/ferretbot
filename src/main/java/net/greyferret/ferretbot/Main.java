package net.greyferret.ferretbot;

import net.greyferret.ferretbot.client.ApiClient;
import net.greyferret.ferretbot.client.ChatClient;
import net.greyferret.ferretbot.client.FerretChatClient;
import net.greyferret.ferretbot.config.ChatConfig;
import net.greyferret.ferretbot.config.DbConfig;
import net.greyferret.ferretbot.config.SpringConfig;
import net.greyferret.ferretbot.listener.FerretBotChatListener;
import net.greyferret.ferretbot.logic.ChatLogic;
import net.greyferret.ferretbot.service.ViewerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Main claSS for running entire FerretBot
 * <p>
 * Created by GreyFerret on 07.12.2017.
 */
public class Main {
	private static final Logger logger = LogManager.getLogger(Main.class);

	/***
	 * Main method for running jar application
	 *
	 * @param args arguments for authorisations
	 */
	public static void main(String[] args) {
		AnnotationConfigApplicationContext annotationConfigApplicationContext = new AnnotationConfigApplicationContext();
		annotationConfigApplicationContext.register(DbConfig.class);
		annotationConfigApplicationContext.register(SpringConfig.class);
		annotationConfigApplicationContext.register(FerretBot.class);
		annotationConfigApplicationContext.register(ChatConfig.class);
		annotationConfigApplicationContext.register(ChatClient.class);
		annotationConfigApplicationContext.register(FerretChatClient.class);
		annotationConfigApplicationContext.register(FerretBotChatListener.class);
		annotationConfigApplicationContext.register(ChatLogic.class);
		annotationConfigApplicationContext.register(ViewerService.class);
		annotationConfigApplicationContext.register(ApiClient.class);
		annotationConfigApplicationContext.refresh();
		SpringApplication.run(Main.class);

		logger.info("Bot started!");
		FerretBot bot = annotationConfigApplicationContext.getBean(FerretBot.class);
		Thread botThread = new Thread(bot);
		botThread.setName("Main Thread");
		botThread.start();
	}
}
