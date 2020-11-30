import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.*;

public class Main {
    public static void main(String[] args) throws IOException, ParserConfigurationException, SAXException {
        File file = new File("data" + File.separator + "skwiki-latest-templatelinks.sql");                     //Načítanie dát z dumpu templatelinks.sql
        LinkedList<String> list = new LinkedList<String>();
        List<String> results = new ArrayList<String>();
        List<Integer> id = new ArrayList<Integer>();
        Scanner scanner = new Scanner(file);
        while (scanner.hasNextLine()) {                                                                                 //Vyhľadanie všetkých riadkov s rozlišovacími stránkami
            list.add(scanner.nextLine());
            if(list.getLast().contains("Rozlišovacia_stránka"))
                results.add(list.getLast());
        }
        for(String str: results) {                                                                                      //Uloženie ID pre všetky rozlišovacie stránky
            Pattern pattern = Pattern.compile("(\\([0-9]*,10,'Rozlišovacia_stránka',0)");
            Matcher matcher = pattern.matcher(str);
            while (matcher.find()) {
                id.add(Integer.parseInt(matcher.group(1).substring(matcher.group(1).indexOf("(") + 1, matcher.group(1).indexOf(",10,'Rozlišovacia_stránka'"))));
            }
        }

        FileWriter writer = new FileWriter("result.json");                                                      //Načítanie dát zo súboru pages-articles.xml
        file = new File("data" + File.separator + "skwiki-latest-pages-articles.xml");
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();
        DefaultHandler handler = new DefaultHandler() {                                                                 //Parsovanie XML, funkcia sa opakuje pokiaľ nepríde na koniec súboru
            String text;
            String temptitle;
            int number = 0;
            boolean page = true;
            boolean pageid = false;
            boolean pagetitle = false;
            boolean pagetext = false;
            boolean temptext = false;
            public void startElement(String uri, String localName, String qName, Attributes attributes) {
                if (qName.equalsIgnoreCase("id") && page) {
                    pageid = true;
                }
                if (qName.equalsIgnoreCase("title")) {
                    pagetitle = true;
                }
                if (qName.equalsIgnoreCase("text")) {
                    pagetext = true;
                }
            }

            public void characters(char[] ch, int start, int length) {                                                  //Skontrolovanie či sa ID zhodujú
                if (pageid && page) {
                    String value = new String(ch, start, length).trim();
                    page = false;
                    if (!id.isEmpty() && Integer.parseInt(value) == id.get(0)) {
                        System.out.println(value);
                        System.out.println(temptitle);
                        temptext = true;
                        text = "";
                        id.remove(0);
                    }
                }
                if (pagetitle) {
                    temptitle = new String(ch, start, length).trim();                                                   //Uloženie názvu stránky
                }
                if (pagetext && temptext) {                                                                             //Uloženie textu na ďaľšie spracovanie
                    String value = new String(ch, start, length);
                    text = text+value;
                }
            }

            public void endElement(String uri, String localName, String qName) {
                if (qName.equalsIgnoreCase("page")) {
                    page = true;
                }
                if (qName.equalsIgnoreCase("id")) {
                    pageid = false;
                }
                if (qName.equalsIgnoreCase("title")) {
                    pagetitle = false;
                }
                if (qName.equalsIgnoreCase("text")) {
                    pagetext = false;
                    if (temptext) {                                                                                     //Parsovanie textu rozlišovacej stránky
                        temptext = false;
                        String lines[] = text.split("\\r?\\n");
                        for (String l: lines) {
                            if (l.contains("== Referencie ==") || l.contains("== Pozri aj =="))
                                break;
                            l = l.replaceAll("\\{\\{--\\}\\}", " - ");                                  //Odstránenie formátovacích znakov
                            l = l.replaceAll("<ref(.*)/>", "");
                            l = l.replaceAll("(<ref(.*)ref>)|(<ref((.|\\n)*)ref>)", "");
                            l = l.replaceAll("<ref(.*)$", "");
                            l = l.replaceAll("&nbsp;", "");
                            l = l.replaceAll("&", "&amp;");
                            l = l.replaceAll("<", "&lt;");
                            l = l.replaceAll(">", "&gt;");
                            l = l.replaceAll("In: ''(.*?)$", "");
                            l = l.replaceAll("In: (.*?)$", "");
                            l = l.replaceAll("\\{\\{(.*?)\\}\\}", "");
                            l = l.replaceFirst("^\\s", "");
                            l = l.replaceAll("''", "");
                            l = l.replaceAll("\\\"", "");
                            if (l.contains("[[") && (l.contains("*") || l.contains("#"))) {
                                l = l.replaceAll("\\*", "");
                                List<String> title = new ArrayList<String>();
                                List<String> anchor = new ArrayList<String>();
                                List<String> replacement = new ArrayList<String>();
                                Pattern pattern = Pattern.compile("\\[\\[(.*?)\\]\\]");                                 //Nájdenie nadpisov a linkov
                                Matcher matcher = pattern.matcher(l);
                                while (matcher.find()) {
                                    title.add(matcher.group(1).replace("[", "").replace("]", ""));
                                    anchor.add(matcher.group(1).replace("[", "").replace("]", ""));
                                }
                                if (!title.isEmpty() && !l.contains(" in:") && !l.contains(" In:")) {
                                    try {
                                        int i = 0;
                                        for (String item : title) {;                                                    //Parsovanie nadpisov a linkov
                                            if (item.contains("|")) {
                                                String temp[] = item.split("\\|");
                                                anchor.set(i, temp[1]);
                                                title.set(i, temp[0]);
                                                replacement.add(temp[1]);
                                            }
                                            else {
                                                replacement.add(item);
                                            }
                                            i++;
                                        }
                                        i = 0;
                                        for (String item : replacement) {                                               //Parsovanie popisku
                                            l = l.replaceFirst("\\[\\[(.*?)\\]\\]", item);
                                        }
                                        for (String item : title) {
                                            i++;
                                        }
                                        for (int j = 0; j < i; j++) {                                                   //Vpísanie údajov do JSON
                                            l = l.replaceFirst("^( *)", "");
                                            writer.write("{\"index\":{\"_index\":\"vinf\",\"_id\":"+number+"}}"+System.getProperty("line.separator"));
                                            writer.write("{\"name\":\""+temptitle+"\",\"title\":\""+title.get(j).substring(0, 1).toUpperCase() + title.get(j).substring(1)+"\",\"anchor\":\""+anchor.get(j)+"\",\"description\":\""+temptitle+" | "+l.substring(0, 1).toUpperCase() + l.substring(1).replaceFirst("(\t*)$", "").replaceAll("\\u00A0", " ").replaceAll("\\\\", "")+"\"}"+System.getProperty("line.separator"));
                                            number++;
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        };
        parser.parse(file, handler);
        writer.close();
    }
}
