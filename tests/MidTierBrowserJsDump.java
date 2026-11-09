import java.util.Base64;
import java.nio.charset.StandardCharsets;
import se.yrell.developertools.midtier.MidTierBrowserScript;
public class MidTierBrowserJsDump {
 static void p(String name,String text){System.out.println(name+"="+Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));}
 public static void main(String[] args) {
   p("state",MidTierBrowserScript.pageState());
   p("check",MidTierBrowserScript.configLoginCheck("https://mt.example/arsys"));
   p("submit",MidTierBrowserScript.submitConfigLogin("https://mt.example/arsys","arserver","tester","p\\\"ss;alert(1)//"));
   p("result",MidTierBrowserScript.resultProbe());
 }
}
