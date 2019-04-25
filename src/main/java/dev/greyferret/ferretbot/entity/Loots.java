package dev.greyferret.ferretbot.entity;

import com.google.gson.internal.LinkedTreeMap;
import dev.greyferret.ferretbot.config.SpringConfig;
import dev.greyferret.ferretbot.entity.json.loots.Ok;
import dev.greyferret.ferretbot.exception.LootsRunningLootsParsingException;
import dev.greyferret.ferretbot.util.FerretBotUtils;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Class for each "Loots", called Loots
 * <p>
 * Created by GreyFerret on 08.12.2017.
 */
@Entity
@Table(name = "loots")
public class Loots implements Serializable {
	@Type(type = "org.hibernate.type.TextType")
	@Column(name = "message")
	private String message;
	@Column(name = "id")
	@Id
	private String id;
	@Column(name = "date")
	private LocalDateTime date;
	@Column(name = "paid")
	private Boolean paid;
	@Column(name = "loots_name")
	private String lootsName;
	@OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.PERSIST)
	@JoinColumn(name = "author", referencedColumnName = "loots_Name")
	private ViewerLootsMap viewerLootsMap;

	private Loots() {
	}

	public Loots(Ok entry) {
		this.message = entry.getAttachments().getMessage();
		this.id = entry.getId();
		this.paid = false;
		String name = FerretBotUtils.parseLootsAuthor(entry.getFrom().getAccount().getName());
		this.lootsName = name;
	}

	/***
	 * Method for advanced parsing of running Loots (that is currently showing)
	 *
	 * @param runningLoots
	 */
	public Loots(LinkedTreeMap<String, java.lang.Object> runningLoots) throws LootsRunningLootsParsingException {
		try {
			this.id = String.valueOf(runningLoots.get("_id"));
			LinkedTreeMap<String, Object> attachments = (LinkedTreeMap<String, Object>) runningLoots.get("attachments");
			this.message = String.valueOf(attachments.get("message"));
			LinkedTreeMap<String, Object> from = (LinkedTreeMap<String, Object>) runningLoots.get("from");
			LinkedTreeMap<String, Object> account = (LinkedTreeMap<String, Object>) from.get("account");
			this.lootsName = FerretBotUtils.parseLootsAuthor((String) account.get("name"));
			this.paid = false;
		} catch (Exception e) {
			throw new LootsRunningLootsParsingException(e);
		}
	}

	@PostPersist
	private void postPersist() {
		ZonedDateTime zdt = ZonedDateTime.now(SpringConfig.getZoneId());
		setDate(zdt);
	}

	@Override
	public String toString() {
		ZonedDateTime zdt = ZonedDateTime.now(SpringConfig.getZoneId());
		String lootsName = this.lootsName;
		String twitchName = this.lootsName;
		if (viewerLootsMap != null) {
			lootsName = viewerLootsMap.getLootsName();
			if (viewerLootsMap.getViewer() != null) {
				twitchName = viewerLootsMap.getViewer().getLogin();
			}
		}
		DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.forLanguageTag("ru"));
		return "Loots(" + this.id + ") " + dateTimeFormatter.format(date) + ": L:" + lootsName + " / T:" + twitchName + ": \"" + this.message + "\"";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Loots loots = (Loots) o;

		return id.equals(loots.id);

	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public ZonedDateTime getDate() {
		if (this.date == null) {
			return null;
		}
		return ZonedDateTime.of(this.date, SpringConfig.getZoneId());
	}

	public void setDate(LocalDateTime date) {
		this.date = date;
	}

	public void setDate(ZonedDateTime date) {
		this.date = date.toLocalDateTime();
	}

	public Boolean getPaid() {
		return paid;
	}

	public void setPaid(Boolean paid) {
		this.paid = paid;
	}

	public String getLootsName() {
		return lootsName;
	}

	public void setLootsName(String lootsName) {
		this.lootsName = lootsName;
	}

	public ViewerLootsMap getViewerLootsMap() {
		return viewerLootsMap;
	}

	public void setViewerLootsMap(ViewerLootsMap viewerLootsMap) {
		this.viewerLootsMap = viewerLootsMap;
	}
}
