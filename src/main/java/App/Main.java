package App;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.util.HashSet;

import javax.naming.TimeLimitExceededException;
import javax.security.auth.login.AccountNotFoundException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Main {

	static String first_template = "Определи сферу из предложенных, а также отвечай только ключевыми фразами в официально-деловом стиле, как в законодательстве, сохраняя смысл слов. "
			+ "Если сферу определить не удается, отвечай <wrong request>. "
			+ "Сферы: Законодательство РФ, льготы бизнесу РФ, бухгалтерский учет бизнеса РФ"
			+ "Запрос: Можно ли открыть вейпшоп в жилом доме? "
			+ "Ответ: Законодательство РФ, магазин, продажа, табачные изделия, табачная продукция, дом, многоквартирный дом. "
			+ "Запрос: Можно ли размещать рекламу сигарет на билборде? "
			+ "Ответ: Законодательство РФ, реклама, табачные изделия, улица, размещение рекламы. ";
	static String second_template = "Необходимо ответить <да>, если пользователь вводит согласие, иначе ответить <нет>"
			+ "Запрос: Да, хочу. "
			+ "Ответ: да. "
			+ "Запрос: Неа. "
			+ "Ответ: нет. ";
	static Database companies;
	static BufferedWriter logging;
	static Parser parser;
	
	public static String takeMessage(String userMessage, String role) throws IOException {
		String apiKey = "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";
		
        URL url = new URL("https://api.openai.com/v1/chat/completions");

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        
        connection.setDoOutput(true);
        String requestBody = "{\"model\":\"gpt-3.5-turbo\",\"temperature\":0.1,\"messages\":[{\"role\":\"assistant\",\"content\":\"" + role + "\"},{\"role\":\"user\",\"content\":\"" + userMessage + "\"}]}";
        connection.getOutputStream().write(requestBody.getBytes("UTF-8"));

        int responseCode = connection.getResponseCode();

        BufferedReader reader;
        if (responseCode == HttpURLConnection.HTTP_OK) {
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        } else {
        	reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        }

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        connection.disconnect();
        
        String message;
        if(responseCode == HttpURLConnection.HTTP_OK) {
	        message = response.substring(response.indexOf("\"content\":") + 11);
	        message = message.substring(0, message.indexOf("\"}"));
        } else
        	throw new IOException(response.toString());
        
        return message;
	}

	public static String getBestResult(String request) {
		try {
	    	String search_site = "https://yandex.ru/search/?text=";
	    	String charset = "UTF-8";
	        
	        URL url = new URL(search_site + URLEncoder.encode(request, charset));
	        Document doc = Jsoup.parse(url, 3000);
	        Elements e = doc.select("div.content__left > ul.serp-list > li");
	        for(Element es : e) {
	        	if(es.select("span.organic__advLabel").text().contains("Реклама"))
	        		continue;
	        	return es.select("div > div > a").attr("href");
	        }
	    } catch(IOException e) {
	    	e.printStackTrace();
	    }
		return "По запросу ничего не найдено!";
	}
	
	public static void main(String[] args) {
		try {
			logging = new BufferedWriter(new FileWriter("assets/log.log", true));
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		companies = new Database("assets/users.csv");
		
		int deep = 0;
		try(ServerSocket server = new ServerSocket(5566);
			Socket socket = server.accept();
			BufferedReader c_input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter c_output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));) {
			
			System.out.println("Клиент подключен по адресу: " + socket.getInetAddress().getHostAddress());
			
			String url = "";
			int state;
			String message;
			while(true) {
				try {
					String companyId = "undefined";
					String userId = "undefined";
					long timestamp = 0;
					String request = "undefined";
					long time = 0;
					Integer[] info = null;
					try {
						companyId = c_input.readLine();
						userId = c_input.readLine();
						timestamp = Long.parseLong(c_input.readLine());
						request = c_input.readLine();
						while(c_input.ready())
							c_input.read();

						HashSet<String> company = companies.companies_info.get(companyId);
						if(company == null)
							throw new AccountNotFoundException("Нет такой компании");
						
						boolean found = false;
						for(String user : company) {
							if(user.equals(userId)) {
								found = true;
								break;
							}
						}
						
						if(!found)
							throw new AccountNotFoundException("Нет такого пользователя");
						
						info = companies.users_info.get(userId);
						if(deep < 1) {
							if(info[0] < 1)
								throw new IllegalAccessException("Превышен лимит запросов от пользователя");
							info[0]--;
						}
						
						
						state = 0;
						
						message = takeMessage("Запрос: " + request, first_template);
						if(message.indexOf("Ответ:") != -1)
							message = message.substring(message.indexOf("Ответ:") + 6);
						else
							throw new IllegalAccessException("Некорректный запрос!");
						System.out.println(message);
						url = getBestResult(message);
						System.out.println(url);
						
						time = new Timestamp(System.currentTimeMillis()).getTime();
						if(time - timestamp > 15000L)
							throw new TimeLimitExceededException("Timeout");
						parser = new Parser(url);
						message = parser.get(message);
					} catch(AccountNotFoundException e) {
						System.err.println(e.getMessage());
						state = 1;
						message = "Недействительна авторизация бота!";
					} catch(IllegalAccessException e) {
						System.err.println(e.getMessage());
						state = -2;
						message = e.getMessage();
					} catch(TimeLimitExceededException e) {
						System.err.println(e.getMessage());
						state = -3;
						message = "Превышено время ожидания запроса!";
					}
					
					c_output.write(Integer.toString(state) + "\r");
					c_output.write(message + "\r");
					c_output.write(url + "\r");
					c_output.flush();
					
					if(state == 0) {
						boolean repeat = false;
						if(deep < 1) {
							String request_2 = c_input.readLine();
							message = takeMessage("Запрос: " + request_2, second_template);
							repeat = message.toLowerCase().contains("да");
							c_output.write(Boolean.toString(repeat) + "\n");
							c_output.flush();
						}
						
						if(!repeat) {
							int rate = Integer.parseInt(c_input.readLine());
							info[1] = rate;
						} else
							deep++;
					}
					
					logging.write("\"" + companyId + "\"");
					logging.write("\"" + userId + "\" - ");
					logging.write(Long.toString(timestamp) + " - ");
					logging.write(Long.toString(time) + " - ");
					logging.write(state == 0 ? "no_error" : message);
					logging.write("\n");
					logging.flush();
				} catch(Exception e) {
					System.err.println(e.getMessage());
					break;
				}
			}
			
			System.out.println("Клиент отключен!");
		} catch (IOException e) {
			System.err.println("Exception happened on server, caused to\n" + e.getMessage());
		}
		
		try {
			companies.close();
			logging.close();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

}
