package App;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Queue;

import javax.security.auth.login.AccountNotFoundException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Main {
	//шаблоны поведения для chat gpt (они записаны в assets/templates.txt построчно каждый шаблон
	static String first_template = ""; //разбиение запроса пользователя на ключевые слова для поиска, а также выявления сферы запроса
	static String second_template = ""; //определение согласия пользователя для простоты
	static String third_template = ""; //если нам необходимо не найти информацию, а получить совет, то этот шаблон выдает по запросу пользователя советы
	static ArrayList<String> apiKeys = new ArrayList<String>(); //сюда записываем api ключи для подключения к chat gpt. он хранится в файле assets/key.txt. Наш ключ не работает, так как гитхаб его блокирует из-за публичности репозитория(((
	final static String chatgpt_url = "https://api.openai.com/v1/chat/completions";
	static Database companies; //база данных для комании. Тут просто парсер удобный эксельки и поиск по ней. Возможно расширение в sql в классе Database
	static Log log; //Объект класса для логирования. Каждый такой экземплярчик может писать в нужный ему файл. логи находятся по пути assets/log.log
	
	static ArrayDeque<Long> request_queue; //каждый запрос отправляет сюда временную метку
	static int pos = 0;
	static Parser parser; //Объект класса для парсинга данных с сайтов. yandex.ru/search и результатных сайтов, где скорее всего содержится ответ на запрос пользователя
	
	/* Метод для обработки сообщения через chat gpt по заданному apiKey. По заданному шаблону запросника зависит для чего chat gpt обрабатывает запрос
	 * userMessage - подается запрос пользователя в формате <Запрос: СЮДА_ПИШЕМ_САМ_ЗАПРОС>
	 * role - шаблон поведения, на выбор - first_template, second_template, third_template
	 */
	public static String takeMessage(String userMessage, String role) throws IOException, InterruptedException {
		long near_time = request_queue.size() == 0 ? 0L : (request_queue.element() + 20000L - System.currentTimeMillis()); //время до ближайшего занятого запроса
		if(near_time < 0L) {
			request_queue.remove();
		}
		if(request_queue.size() >= apiKeys.size() * 3)
			throw new InterruptedException("Запросы отправляются слишком часто. Сервер перегружен! Ближайшее время ожидания для запроса: " + near_time + "ms.");
        URL url = new URL(chatgpt_url); //сайт к которому подключаемся к чат гпт. у нас это официальный. нет блокировки из России)))

        //просто подключение по протоколу HTTP. Там на сайтике апишки указано как к нему обращаться))))))
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKeys.get(pos));
        
        request_queue.add(System.currentTimeMillis());
        if(request_queue.size() % 3 == 0)
        	pos = (pos + 1) % apiKeys.size();
        
        connection.setDoOutput(true);
        //это просто json записанный в string. ничего примечательного, зато либы всякие не нужны
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
        	//ну если у нас все хорошо, то берем ответ чат гпт (json) и выпарсиваем из него ответ для нашего пользователя, убирая всякие ненужные знаки
	        message = response.substring(response.indexOf("\"content\":") + 11);
	        message = message.substring(0, message.indexOf("}"));
	        
	        //чат гпт имеет свойство плевать всякие знаки некрасивые. вот такие мы нашли и убираем циклично. костыль, но рабочий и не самый медленный
	        int pos = 0;
	        while((pos = message.indexOf("\"")) != -1)
	        	message = message.substring(0, pos) + message.substring(pos + 1);
	        while((pos = message.indexOf("\\n\\n")) != -1)
	        	message = message.substring(0, pos) + message.substring(pos + 4);
        } else
        	throw new ConnectException(response.toString()); //если ключ апи не рабочий или много запросов в минуту отправили, то проще ошибку выкинуть и в логе сказать, что не так
        
        //кстати бесплатный чат гпт имеет ограничение 3 запроса в минуту.... поэтому нам пришлось ровно в них уложиться((( у платного такого нет, но у нас платного нет
        return message;
	}

	/* хм... ну есть у нас яндекс, есть ключевые слова, которые отлично ищутся. google сложно приспособить к определенно русскому сегменту,
	 * поэтому чисто парсинг яндекс поиска. он, кстати, не выдает никаких капч и тд, сколько не пробовал. Если у вас выдаст, то нужно искать хороший поисковик.
	 */
	public static String getBestResult(String request) {
		try {
	    	String search_site = "https://yandex.ru/search/?text=";
	    	String charset = "UTF-8";
	        
	        URL url = new URL(search_site + URLEncoder.encode(request, charset));
	        Document doc = Jsoup.parse(url, 3000);
	        Elements e = doc.select("div.content__left > ul.serp-list > li");
	        //яндекс очень сложный и неприятный сайт для парсинга. просто вырезаем все рекламные ссылки, они нам ненужны и ищем первую нерекламную
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
			log = new Log("assets/log.log"); //подгрузка логов
			request_queue = new ArrayDeque<Long>();
			
			companies = new Database("assets/users.csv"); //подгрузка базы данных

			BufferedReader fis = new BufferedReader(new FileReader("assets/key.txt")); //чтение апи ключа
			String key;
			while((key = fis.readLine()) != null)
				apiKeys.add(key);
			fis.close();
			
			fis = new BufferedReader(new FileReader("assets/templates.txt")); //шаблончики. каждый шаблон - одна строка. это строго
			first_template = fis.readLine();
			second_template = fis.readLine();
			third_template = fis.readLine();
			fis.close();
		} catch (IOException e) {
			e.printStackTrace(); //если ошибка тут была, то смысл вообще работать серверу? данных то не хватает для работы
			return;
		}
		
		int deep = 0; //глубина запроса. изначально 0, а если уточняем, то 1. дальше по коду понятно станет зачем
		
		//размещаем сервер на локалхосте по 5566 порту
		try(ServerSocket server = new ServerSocket(5566);
			Socket socket = server.accept();
			BufferedReader c_input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			BufferedWriter c_output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));) {
			
			System.out.println("Клиент подключен по адресу: " + socket.getInetAddress().getHostAddress());
			
			String url = "";
			int state;
			String message;
			long request_timestamp = 0L;
			
			while(true) {
				try {
					//информация, которую будем читать от бота или искать в базе данных или просто временные фиксации
					String companyId = "undefined";
					String userId = "undefined";
					long timestamp = 0;
					String request = "undefined";
					long time = 0;
					Integer[] info = null;
					
					try {
						//читаем от бота компанию, пользователя, время и сам запрос, конечно
						companyId = c_input.readLine();
						userId = c_input.readLine();
						timestamp = Long.parseLong(c_input.readLine());
						request = c_input.readLine();
						while(c_input.ready()) //это, конечно, ненужно, но на всякий... времени и опыта коммерции на хороший REST API не хватило(((
							c_input.read();
						time = System.currentTimeMillis();
						
						HashSet<String> company = Main.companies.companies_info.get(companyId); //ищем в бд компанию
						if(company == null)
							throw new AccountNotFoundException("Нет такой компании.");
						
						boolean found = false;
						for(String user : company) { //каждый пользователь привязан к компании. ищем в этой компании этого юзера
							if(user.equals(userId)) {
								found = true;
								break;
							}
						}
						
						if(!found)
							throw new AccountNotFoundException("Нет такого пользователя.");
						
													
						info = Main.companies.users_info.get(userId); //дальше собираем о нем информацию
						if(deep < 1) {
							if(info[0] < 1) //если пользователь навсегда исчерпал свой лимит
								throw new IllegalAccessException("Превышен лимит запросов от пользователя.");
							info[0]--;
						}
						
						
						state = 0;
						request_timestamp = System.currentTimeMillis();
						
						//отправляем запрос в сыром виде в чат гпт и получаем ключевые слова через запятую
						message = takeMessage("Запрос: " + request, first_template);
						if(message.indexOf("Ответ:") != -1)
							message = message.substring(message.indexOf("Ответ:") + 6);
						else
							throw new IllegalAccessException("Некорректный запрос!");
						if(message.contains("помощь в бизнесе")) {
							//5-ый пункт. если пользователю нужен совет, то искать ничего не надо, спросим у эксперта
							url = "";
							
							message = takeMessage("Запрос: " + request, third_template);
							if(message.indexOf("Ответ:") != -1)
								message = message.substring(message.indexOf("Ответ:") + 6);
						} 
						else {
							//иначе (первые 3 пункта) тупо ищем нужный сайт в яндексе
							url = getBestResult(message);

							Parser parser = new Parser(url);
							message = parser.get(message);
						}
					} catch(AccountNotFoundException e) {
						//если не нашли компанию/пользователя => бот не авторизован, либо просто нужна переавторизация
						Main.log.printExceptionViaInfo(e, userId, companyId, request);
						state = 1;
						message = "Недействительна авторизация бота!";
					} catch(IllegalAccessException e) {
						//если пользователь не может пользоваться благами бота
						Main.log.printExceptionViaInfo(e, userId, companyId, request);
						state = -2;
						message = e.getMessage();
					} catch(ConnectException e) {
						//если у сервера проблемы с общением с чат гпт например
						Main.log.printExceptionViaInfo(e, userId, companyId, request);
						state = -3;
						message = "Попробуйте повторить запрос позже!";
					} catch(InterruptedException e) {
						//Ну и если пользователь спамит запросы сверх ограничения
						Main.log.printExceptionViaInfo(e, userId, companyId, request);
						state = -4;
						message = "Нельзя отправлять чаще одного запроса в минуту!. Повторите запрос позже!";
					}
					
					//отправляем ответ, вместе с ссылкой. ну а если ошибка была, то сообщаем и об этом
					c_output.write(Integer.toString(state) + "\r");
					c_output.write(message + "\r");
					c_output.write(url + "\r");
					c_output.flush();
					
					if(state == 0) { //проверка на ошибки
						boolean repeat = false;
						if(deep < 1) {
							//спрашиваем нужно ли уточнять запрос. deep < 1 это как много раз нужно переспрашивать об уточнении
							String request_2 = c_input.readLine();
							message = takeMessage("Запрос: " + request_2, second_template);
							repeat = message.toLowerCase().contains("да");
							c_output.write(Boolean.toString(repeat) + "\n");
							c_output.flush();
						}
						
						if(!repeat) {
							//просим оценить работу бота)) увы, но это обязательно
							int rate = Integer.parseInt(c_input.readLine());
							info[1] = rate;
							deep = 0;
						} else
							deep++;
						
						//логируем запрос пользователя
						log.append(new Date(timestamp) + " - \"" + companyId + "\" - \"" + userId + "\" - "
								+ " - " + request, time);
					}
				} catch(Exception e) {
					e.printStackTrace();
					break;
				}
			}
			//дальше все. сервак закрыт
			System.out.println("Клиент отключен!");
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Exception happened on server, caused to\n\t" + e.getMessage());
		}
		
		try {
			companies.close();
			log.close();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

}
