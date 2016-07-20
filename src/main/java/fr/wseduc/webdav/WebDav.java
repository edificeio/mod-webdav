/*
 * Copyright © WebServices pour l'Éducation, 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.wseduc.webdav;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.impl.SardineImpl;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.*;
import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;

public class WebDav extends BusModBase implements Handler<Message<JsonObject>> {

	private JsonObject credentials;

	@Override
	public void start() {
		super.start();
		credentials = config.getObject("credentials", new JsonObject());
		vertx.eventBus().registerHandler(config.getString("address", "webdav"), this);
	}

	@Override
	public void handle(Message<JsonObject> message) {
		String action = message.body().getString("action", "");
		switch (action) {
			case "put":
				put(message);
				break;
			default:
				sendError(message, "Invalid action.");
		}
	}

	private void put(Message<JsonObject> message) {
		String filePath = message.body().getString("file");
		if (filePath == null || filePath.trim().isEmpty()) {
			sendError(message, "Invalid file.");
			return;
		}
		FileInputStream fis;
		try {
			fis = new FileInputStream(filePath);
		} catch (FileNotFoundException e) {
			sendError(message, "Invalid file.", e);
			return;
		}
		String uri = message.body().getString("uri");
		Sardine sardine = getSardine(uri, message);
		if (sardine == null) return;
		try {
			sardine.put(uri, fis);
			sendOK(message);
		} catch (IOException e) {
			sendError(message, e.getMessage(), e);
		}
	}

	private Sardine getSardine(String uri, Message<JsonObject> message) {
		String host;
		try {
			host = new URI(uri).getHost();
		} catch (URISyntaxException e) {
			sendError(message, e.getMessage(), e);
			return null;
		}
		JsonObject credential = credentials.getObject(host);
		Sardine sardine;
		if (credential != null) {
			if (credential.getBoolean("insecure", false)) {
				sardine = new SardineImpl() {
					@Override
					protected ConnectionSocketFactory createDefaultSecureSocketFactory() {
						SSLConnectionSocketFactory sf = null;
						TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {

							@Override
							public java.security.cert.X509Certificate[] getAcceptedIssuers() {
								return null;
							}

							@Override
							public void checkClientTrusted(java.security.cert.X509Certificate[] certs,
									String authType) {
							}

							@Override
							public void checkServerTrusted(java.security.cert.X509Certificate[] certs,
									String authType) {
							}
						} };
						try {
						SSLContext context = SSLContext.getInstance("TLS");
						context.init(null, trustAllCerts, null);

						 sf = new SSLConnectionSocketFactory(context,
								 SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
						} catch (NoSuchAlgorithmException | KeyManagementException e) {
							logger.error(e.getMessage(), e);
						}
						return sf;
					}
				};
				sardine.setCredentials(
						credential.getString("username"), credential.getString("password"));
			} else {
				sardine = SardineFactory.begin(
						credential.getString("username"), credential.getString("password"));
			}
			sardine.enablePreemptiveAuthentication(host);
		} else {
			sardine = SardineFactory.begin();
		}
		return sardine;
	}

}
