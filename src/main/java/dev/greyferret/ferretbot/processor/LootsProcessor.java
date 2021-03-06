package dev.greyferret.ferretbot.processor;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import dev.greyferret.ferretbot.config.*;
import dev.greyferret.ferretbot.entity.Loots;
import dev.greyferret.ferretbot.entity.json.account.AccountJson;
import dev.greyferret.ferretbot.entity.json.loots.LootsJson;
import dev.greyferret.ferretbot.entity.json.loots.Ok;
import dev.greyferret.ferretbot.exception.LootsRunningLootsParsingException;
import dev.greyferret.ferretbot.service.LootsService;
import dev.greyferret.ferretbot.service.ViewerService;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;

/**
 * Loots bot
 * <p>
 * Created by GreyFerret on 07.12.2017.
 */
@Component
@EnableConfigurationProperties({StreamelementsConfig.class, LootsConfig.class, ChatConfig.class, ApplicationConfig.class, BotConfig.class})
@Log4j2
public class LootsProcessor implements Runnable, ApplicationListener<ContextStartedEvent> {
	@Autowired
	private LootsConfig lootsConfig;
	@Autowired
	private LootsService lootsService;
	@Autowired
	private ViewerService viewerService;
	@Autowired
	private ApplicationContext context;
	@Autowired
	private ChatConfig chatConfig;
	@Autowired
	private ApplicationConfig applicationConfig;
	@Autowired
	private PointsProcessor pointsProcessor;
	@Autowired
	private BotConfig botConfig;

	private long timeRetryMS;
	private boolean isOn;
	private Map<String, String> cookies;
	private final String loginUrl = "https://loots.com/pub/auth/login";
	private final String accountUrl = "https://loots.com/en/account";
	private final String lootsUrl = "https://loots.com/api/v1/me/transactions/tips/broadcaster";
	private String key;
	private String token;
	private String tokenChroma;

	/***
	 * Constructor with all params for Loots
	 */
	public LootsProcessor() {
		this.isOn = true;
		this.cookies = new HashMap<>();
	}

	@PostConstruct
	private void postConstruct() {
		this.timeRetryMS = lootsConfig.getTimer().getDefaultRetryMs();
		pointsProcessor = context.getBean(PointsProcessor.class);
	}

	public void run() {
		boolean retryLogin = true;
		while (retryLogin) {
			if (cookies == null || cookies.size() == 0) {
				log.info("No cookies found, starting auth...");
				retryLogin = true;
				login();
			}
			if (StringUtils.isBlank(key) || StringUtils.isBlank(token) || StringUtils.isBlank(tokenChroma)) {
				log.info("No Key/Token/TokenChroma found, starting auth...");
				retryLogin = true;
				login();
			} else {
				retryLogin = false;
				log.info("Success! Loots are ready...");
			}
		}

		try {
			mainLoop();
		} catch (InterruptedException e) {
			log.error("InterruptedException in LootsProcessor.java", e);
		}
	}

	/***
	 * Close for LootsBot
	 */
	public synchronized void close() {
		this.isOn = false;
	}

	/***
	 * Main loop for LootsBot to continue checking for loots
	 *
	 * @throws InterruptedException
	 */
	private synchronized void mainLoop() throws InterruptedException {
		while (isOn) {
			Thread.sleep(this.timeRetryMS);
			Connection.Response response = null;
			try {
				Map<String, String> headers = new HashMap<>();
				headers.put("Host", "loots.com");
				headers.put("Connection", "keep-alive");
				headers.put("loots-Nonce", "1");
				headers.put("Content-Type", "application/json");
				headers.put("Accept", "application/json");
				headers.put("loots-Access-Token", token);
				headers.put("loots-Client-Key", key);
				headers.put("Referer", "https://loots.com/en/account/tips/condensed/completed");
				headers.put("Accept-Encoding", "gzip, deflate, br");
				headers.put("Accept-Language", "en-US,en;q=0.9");
				response = Jsoup.connect(lootsUrl)
						.method(Connection.Method.GET)
						.ignoreContentType(true)
						.headers(headers)
						.cookies(cookies)
						.execute();
			} catch (IOException e) {
				log.error("Could not request page", e);
				increaseRetry();
			}
			if (response != null) {
				if (response.url().toString().contains("/auth/login")) {
					log.info("Login page found, starting auth...");
					login();
					continue;
				}
				if (StringUtils.isNotBlank(response.body())) {
					Gson g = new Gson();
					LootsJson lootsJson = null;
					try {
						lootsJson = g.fromJson(response.body(), LootsJson.class);
					} catch (Exception e) {
						increaseRetry();
						log.error("Exception when parsing JSON", e);
					}
					if (lootsJson != null) {
						Set<Loots> loots = parseLootsJson(lootsJson);
						lootsService.checkOutLoots(loots);
						ApiProcessor apiProcessor = context.getBean(ApiProcessor.class);
						if (apiProcessor.getChannelStatus()) {
							givePointsForLoots();
						}
						resetRetry();
					} else {
						increaseRetry();
						log.warn("No Loots found, but without exceptions");
					}
				}
			}
		}
	}

	/***
	 * Method that parse Json
	 *
	 * @param input Special Entity for Loots Json
	 * @return Parsed Loots
	 */
	private Set<Loots> parseLootsJson(LootsJson input) {
		Set<Loots> res = new HashSet<>();
		List<Ok> okLoots = input.getData().getOk();
		try {
			ArrayList<Object> runningLootsUnparsed = (ArrayList<Object>) input.getData().getRunning();
			if (runningLootsUnparsed.size() > 0) {
				LinkedTreeMap<String, Object> runningLoots = (LinkedTreeMap<String, Object>) runningLootsUnparsed.get(0);
				try {
					res.add(new Loots(runningLoots, applicationConfig.getZoneId()));
				} catch (LootsRunningLootsParsingException e) {
					log.error("Could not parse Running Loots", e);
				}
			}
		} catch (Exception e) {
			log.error("Could not parse running Loots", e);
		}
		List<Ok> newOkLoots = new ArrayList<>();
		for (Ok ok : okLoots) {
			if (!ok.getType().equalsIgnoreCase("tip_auto")) {
				newOkLoots.add(ok);
			}
		}
		okLoots = newOkLoots;
		Set<Loots> lootsList = new HashSet<>();
		for (Ok okLoot : okLoots) {
			lootsList.add(new Loots(okLoot, applicationConfig.getZoneId()));
		}
		if (lootsList != null && lootsList.size() > 0) {
			res.addAll(lootsList);
			return res;
		}
		return null;
	}

	/***
	 * Reset retry time after successful retrieving of loots
	 */
	private void resetRetry() {
		this.timeRetryMS = this.lootsConfig.getTimer().getDefaultRetryMs();
	}

	/***
	 * Increasing retry time after any expected error while retrieving of loots
	 */
	private void increaseRetry() {
		if (this.timeRetryMS < this.lootsConfig.getTimer().getMaxRetryMs()) {
			this.timeRetryMS = this.timeRetryMS + this.lootsConfig.getTimer().getAdditionalRetryMs();
		}
	}

	/***
	 * Вход в аккаунт
	 */
	private void login() {
		makeLoginRequest();
		makeAccountRequest();
	}

	private void makeAccountRequest() {
		Connection.Response response = null;
		Map<String, String> headers = new HashMap<>();
		headers.put("Connection", "keep-alive");
		headers.put("Referer", "https://loots.com/en/auth/login");
		try {
			log.info("Getting additional info for Loots");
			response = Jsoup.connect(this.accountUrl)
					.headers(headers)
					.cookies(cookies)
					.method(Connection.Method.GET)
					.ignoreContentType(true)
					.execute();
		} catch (IOException e) {
			increaseRetry();
			log.error("Could not get account page of Loots", e);
		}

		String body = response.body();
		Document parse = Jsoup.parse(body);
		Elements elementsByAttribute = parse.getElementsByAttribute("data-env");
		if (elementsByAttribute.size() == 1) {
			Element element = elementsByAttribute.get(0);
			Attributes attributes = element.attributes();
			String data_globals = attributes.get("data-globals");
			Gson gson = new Gson();
			AccountJson account = gson.fromJson(data_globals, AccountJson.class);
			key = account.getApi().getKey();
			token = account.getSession().getAccount().getToken();
			tokenChroma = account.getSession().getAccount().getTokenChroma();
		}
	}

	private void makeLoginRequest() {
		Connection.Response response = null;
		final String requestBody = "{ \"email\": \"" + lootsConfig.getLogin() + "\", \"password\": \"" + lootsConfig.getPassword() + "\"}";
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");

		try {
			log.info("Auth into Loots...");
			response = Jsoup.connect(loginUrl)
					.headers(headers)
					.requestBody(requestBody)
					.method(Connection.Method.POST)
					.ignoreContentType(true)
					.execute();
		} catch (IOException e) {
			increaseRetry();
			log.error("Could not login into Loots", e);
		}
		cookies = response.cookies();
	}

	private void givePointsForLoots() {
		Set<Loots> lootsEntries = lootsService.getUnpaidLoots();
		for (Loots loots : lootsEntries) {
			if (loots.getViewerLootsMap().getViewer().getLogin().equalsIgnoreCase(chatConfig.getChannel())) {

			} else {
				viewerService.addPoints(loots.getViewerLootsMap().getViewer().getLogin(), lootsConfig.getPointsForLoots());
				pointsProcessor.updatePoints(loots.getViewerLootsMap().getViewer().getLogin(), lootsConfig.getPointsForLoots());
			}
		}
	}

	@Override
	public void onApplicationEvent(ContextStartedEvent contextStartedEvent) {
		if (botConfig.isLootsOn()) {
			Thread thread = new Thread(this);
			thread.setName("Loots Thread");
			thread.start();
			log.info(thread.getName() + " started");
		} else {
			log.info("Loots off");
		}
	}
}
