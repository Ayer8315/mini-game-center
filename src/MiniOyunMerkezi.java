import java.awt.*;
import java.awt.event.*;
import java.awt.font.TextLayout;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.Timer;

/**
 * MİNİ OYUN MERKEZİ
 *
 * Ekranlar:
 *  1) Giriş ekranı : Logo, OYNA düğmesi, 1 kişilik / 2 kişilik seçimi
 *  2) Oyun merkezi : 10 oyun kartı (2 tanesi oynanabilir, 8 tanesi "yakında")
 *  3) Yılan oyunu  : Tek kişilik (1 yılan) veya iki kişilik (yeşil + mavi yılan)
 *  4) Hafıza oyunu : 10x5 kart (25 çift). Tek kişilikte rakip bilgisayar.
 *
 * Görseller ve sesler kodla üretilir; ayrı resim veya ses dosyası gerekmez.
 */
public class MiniOyunMerkezi extends JFrame {

	private static final long serialVersionUID = 1L;

	// =====================================================================
	// TEMA: renkler ve yazı tipleri
	// =====================================================================
	static final Color ARKA_UST = new Color(12, 8, 38);
	static final Color ARKA_ALT = new Color(38, 10, 72);
	static final Color CAMGOBEGI = new Color(0, 229, 255);
	static final Color PEMBE = new Color(255, 46, 196);
	static final Color MOR = new Color(140, 82, 255);
	static final Color LIME = new Color(140, 255, 90);
	static final Color SARI = new Color(255, 214, 10);
	static final Color SOLUK = new Color(176, 166, 226);
	static final Color MUREKKEP = new Color(29, 18, 56);

	static final String AILE = aileSec("Segoe UI", "Arial", "SansSerif");
	static final String KALIN_AILE = aileSec("Segoe UI Black", "Arial Black", "Segoe UI", "SansSerif");

	/** Seçilen mod: 1 = tek kişilik (bilgisayara karşı), 2 = iki kişilik. */
	static int oyuncuSayisi = 1;
	/** Animasyonlar için geçen toplam süre (saniye). */
	static double zaman = 0;
	static final Random RASTGELE = new Random();

	/** Yılanların renk paleti. */
	record Palet(String ad, Color murekkep, Color ana, Color acik, Color koyu, Color karin, Color bas) {
	}

	static final Palet YESIL_YILAN = new Palet("YEŞİL", new Color(0x123a0b), new Color(0x3aa51c),
			new Color(0x5cc834), new Color(0x1f7a0e), new Color(0xc4ec5a), new Color(0x46b526));
	static final Palet MAVI_YILAN = new Palet("MAVİ", new Color(0x0b2154), new Color(0x2f7bff),
			new Color(0x63a4ff), new Color(0x1a4fc0), new Color(0xbfe2ff), new Color(0x4a8dff));

	/** Her ekranın her karede çağrılan metodu. */
	interface Ekran {
		void guncelle(double dt);
	}

	// =====================================================================
	// PENCERE: ekranlar arasında geçiş (CardLayout)
	// =====================================================================
	private final CardLayout kartDuzeni = new CardLayout();
	private final JPanel icerik = new JPanel(kartDuzeni);
	private final Map<String, Ekran> ekranlar = new HashMap<>();
	private final YilanOyunu yilanOyunu;
	private final HafizaOyunu hafizaOyunu;
	private String aktifEkran = "giris";
	private long sonKare = System.nanoTime();

	public MiniOyunMerkezi() {
		super("Mini Oyun Merkezi");

		GirisEkrani giris = new GirisEkrani(this);
		MerkezEkrani merkez = new MerkezEkrani(this);
		yilanOyunu = new YilanOyunu(this);
		hafizaOyunu = new HafizaOyunu(this);

		ekranEkle("giris", giris);
		ekranEkle("merkez", merkez);
		ekranEkle("yilan", yilanOyunu);
		ekranEkle("hafiza", hafizaOyunu);
		setContentPane(icerik);

		setIconImage(pencereIkonu());
		setSize(1280, 800);
		setMinimumSize(new Dimension(1040, 700));
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// Pencereden çıkılınca yılan oyunu kendiliğinden duraklar.
		addWindowFocusListener(new WindowAdapter() {
			@Override
			public void windowLostFocus(WindowEvent e) {
				yilanOyunu.duraklat();
			}
		});

		// Oyun döngüsü: saniyede yaklaşık 60 kare.
		new Timer(15, e -> kare()).start();
	}

	private void ekranEkle(String ad, JPanel panel) {
		icerik.add(panel, ad);
		ekranlar.put(ad, (Ekran) panel);
	}

	private void kare() {
		long simdi = System.nanoTime();
		double dt = Math.min((simdi - sonKare) / 1e9, 0.1);
		sonKare = simdi;
		zaman += dt;
		ekranlar.get(aktifEkran).guncelle(dt);
	}

	private void goster(String ad) {
		aktifEkran = ad;
		kartDuzeni.show(icerik, ad);
	}

	void girisEkraninaGit() {
		goster("giris");
	}

	void merkezeGit() {
		goster("merkez");
	}

	void yilanAc() {
		yilanOyunu.yeniOyun(oyuncuSayisi);
		goster("yilan");
		yilanOyunu.odakla();
	}

	void hafizaAc() {
		hafizaOyunu.yeniOyun(oyuncuSayisi);
		goster("hafiza");
		hafizaOyunu.odakla();
	}

	void modSec(int kisi) {
		if (oyuncuSayisi != kisi) {
			oyuncuSayisi = kisi;
			Ses.secim();
		}
		icerik.repaint();
	}

	// =====================================================================
	// YARDIMCI METOTLAR
	// =====================================================================
	static String aileSec(String... adaylar) {
		Set<String> var = new HashSet<>(
				Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
		for (String aday : adaylar) {
			if (var.contains(aday)) {
				return aday;
			}
		}
		return Font.SANS_SERIF;
	}

	static Font yazi(int stil, float boy) {
		return new Font(AILE, stil, 12).deriveFont(stil, boy);
	}

	static Font kalin(float boy) {
		int stil = KALIN_AILE.contains("Black") ? Font.PLAIN : Font.BOLD;
		return new Font(KALIN_AILE, stil, 12).deriveFont(stil, boy);
	}

	static Graphics2D hazirla(Graphics g0) {
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		return g;
	}

	static Color alfa(Color c, int a) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
	}

	static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	static double sinirla(double v, double min, double max) {
		return Math.max(min, Math.min(max, v));
	}

	static double geriYaylan(double t) {
		double p = t - 1;
		return Math.max(0, 1 + 2.7 * p * p * p + 1.7 * p * p);
	}

	static Ellipse2D daire(double cx, double cy, double r) {
		return new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r);
	}

	/** Bir şekli boyar ve koyu kontur çizer. */
	static void boya(Graphics2D g, Shape s, Color dolgu) {
		g.setColor(dolgu);
		g.fill(s);
		g.setColor(MUREKKEP);
		g.draw(s);
	}

	/** Metni ortalanmış şekil olarak döndürür (ışıltı efektleri için). */
	static Shape yaziSekli(Graphics2D g, String metin, Font f, double cx, double taban) {
		TextLayout tl = new TextLayout(metin, f, g.getFontRenderContext());
		return tl.getOutline(AffineTransform.getTranslateInstance(cx - tl.getAdvance() / 2, taban));
	}

	/** Neon ışıltılı yazı. */
	static void isikliYazi(Graphics2D g, String metin, Font f, double cx, double taban, Paint dolgu, Color isik,
			double guc) {
		Shape s = yaziSekli(g, metin, f, cx, taban);
		for (int i = 5; i >= 1; i--) {
			g.setColor(alfa(isik, (int) (guc * (12 + (5 - i) * 9))));
			g.setStroke(new BasicStroke(i * 3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(s);
		}
		g.setPaint(dolgu);
		g.fill(s);
	}

	static void ortaYazi(Graphics2D g, String metin, Font f, double cx, double taban, Color renk) {
		g.setFont(f);
		g.setColor(renk);
		FontMetrics fm = g.getFontMetrics();
		g.drawString(metin, (float) (cx - fm.stringWidth(metin) / 2.0), (float) taban);
	}

	/** Koyu kontürlü, büyüyerek beliren yazı (puan yazıları için). */
	static void konturluYazi(Graphics2D g, String metin, Font f, double cx, double cy, Color dolgu, double saydamlik,
			double olcek) {
		if (olcek < 0.02 || saydamlik <= 0) {
			return;
		}
		Graphics2D k = (Graphics2D) g.create();
		TextLayout tl = new TextLayout(metin, f, k.getFontRenderContext());
		Rectangle2D b = tl.getBounds();
		k.translate(cx, cy);
		k.scale(olcek, olcek);
		Shape s = tl.getOutline(AffineTransform.getTranslateInstance(-b.getCenterX(), -b.getCenterY()));
		k.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) sinirla(saydamlik, 0, 1)));
		k.setColor(new Color(36, 16, 79));
		k.setStroke(new BasicStroke(Math.max(4f, f.getSize2D() * 0.22f), BasicStroke.CAP_ROUND,
				BasicStroke.JOIN_ROUND));
		k.draw(s);
		k.setColor(dolgu);
		k.fill(s);
		k.dispose();
	}

	/** Neon kenarlı koyu kutu. */
	static void neonKutu(Graphics2D g, RoundRectangle2D r, Color kenar, int dolguAlfa, double isik) {
		g.setColor(new Color(22, 14, 58, dolguAlfa));
		g.fill(r);
		for (int i = 3; i >= 1; i--) {
			g.setColor(alfa(kenar, (int) (isik * 34 / i)));
			g.setStroke(new BasicStroke(i * 4f));
			g.draw(r);
		}
		g.setColor(alfa(kenar, 230));
		g.setStroke(new BasicStroke(2f));
		g.draw(r);
	}

	/** Oyun içinde çizilen (gerçek Swing düğmesi olmayan) düğme alanı. */
	record DugmeAlani(Rectangle2D alan, Runnable is) {
	}

	static void cizilmisDugme(Graphics2D g, Rectangle2D r, String metin, Color r1, Color r2, boolean uzerinde) {
		RoundRectangle2D rr = new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(),
				r.getHeight(), r.getHeight());
		for (int i = 3; i >= 1; i--) {
			g.setColor(alfa(r2, uzerinde ? 60 : 30));
			g.setStroke(new BasicStroke(i * 4f));
			g.draw(rr);
		}
		g.setPaint(new GradientPaint((float) r.getX(), 0, r1, (float) r.getMaxX(), 0, r2));
		g.fill(rr);
		g.setPaint(new GradientPaint(0, (float) r.getY(), alfa(Color.WHITE, uzerinde ? 120 : 80), 0,
				(float) r.getCenterY(), alfa(Color.WHITE, 0)));
		g.fill(rr);
		Font f = kalin(17);
		FontMetrics fm = g.getFontMetrics(f);
		ortaYazi(g, metin, f, r.getCenterX(), r.getCenterY() + fm.getAscent() / 2.0 - 3, Color.WHITE);
	}

	/**
	 * Oyunların üstüne çıkan bilgi paneli (başla, duraklat, oyun bitti).
	 * Çizilen düğmelerin yerlerini döndürür ki fare tıklamaları yakalanabilsin.
	 */
	static List<DugmeAlani> bilgiPaneli(Graphics2D g, int W, int H, String baslik, Color renk, String[] satirlar,
			String[] dugmeYazilari, Runnable[] isler, Point fare, BiConsumer<Graphics2D, Rectangle2D> susleme) {
		double susYuksekligi = susleme != null ? 120 : 0;
		double pw = 560, ph = 128 + susYuksekligi + satirlar.length * 30 + 84;
		double px = (W - pw) / 2, py = (H - ph) / 2;
		g.setColor(new Color(8, 4, 24, 150));
		g.fillRect(0, 0, W, H);
		RoundRectangle2D p = new RoundRectangle2D.Double(px, py, pw, ph, 32, 32);
		neonKutu(g, p, renk, 238, 1.2);
		if (susleme != null) {
			susleme.accept(g, new Rectangle2D.Double(px, py + 14, pw, susYuksekligi));
		}
		double y = py + susYuksekligi + 70;
		isikliYazi(g, baslik, kalin(40), W / 2.0, y, Color.WHITE, renk, 1.1);
		y += 44;
		for (String satir : satirlar) {
			ortaYazi(g, satir, yazi(Font.BOLD, 17), W / 2.0, y, new Color(225, 220, 255));
			y += 30;
		}
		List<DugmeAlani> alanlar = new ArrayList<>();
		double dw = 210, dh = 52, aralik = 22;
		double toplam = dugmeYazilari.length * dw + (dugmeYazilari.length - 1) * aralik;
		double dx = W / 2.0 - toplam / 2, dy = py + ph - dh - 26;
		Color[][] renkler = { { CAMGOBEGI.darker(), PEMBE }, { new Color(70, 50, 150), new Color(110, 70, 200) } };
		for (int i = 0; i < dugmeYazilari.length; i++) {
			Rectangle2D r = new Rectangle2D.Double(dx + i * (dw + aralik), dy, dw, dh);
			boolean uzerinde = fare != null && r.contains(fare);
			Color[] rk = renkler[Math.min(i, 1)];
			cizilmisDugme(g, r, dugmeYazilari[i], rk[0], rk[1], uzerinde);
			alanlar.add(new DugmeAlani(r, isler[i]));
		}
		return alanlar;
	}

	/** Parçacık efekti (yem yenince, eşleşme bulununca, konfeti). */
	static final class Parcacik {
		double x, y, vx, vy, yercekimi, omur, yas, boy, don;
		Color renk;
		boolean yuvarlak;

		boolean guncelle(double dt) {
			yas += dt;
			vy += yercekimi * dt;
			x += vx * dt;
			y += vy * dt;
			return yas < omur;
		}

		void ciz(Graphics2D g) {
			double k = 1 - yas / omur;
			g.setColor(alfa(renk, (int) (Math.min(k * 2, 1) * 255)));
			if (yuvarlak) {
				g.fill(daire(x, y, boy * k));
			} else {
				Graphics2D r = (Graphics2D) g.create();
				r.translate(x, y);
				r.rotate(don + yas * 7);
				r.fill(new Rectangle2D.Double(-boy / 2, -boy / 4, boy, boy / 2));
				r.dispose();
			}
		}
	}

	static void patlama(List<Parcacik> liste, double x, double y, Color renk, int adet, double hiz, double boy,
			double yercekimi) {
		for (int i = 0; i < adet; i++) {
			Parcacik p = new Parcacik();
			double aci = RASTGELE.nextDouble() * Math.PI * 2;
			double v = hiz * (0.25 + RASTGELE.nextDouble());
			p.x = x;
			p.y = y;
			p.vx = Math.cos(aci) * v;
			p.vy = Math.sin(aci) * v;
			p.yercekimi = yercekimi;
			p.omur = 0.35 + RASTGELE.nextDouble() * 0.4;
			p.boy = boy * (0.5 + RASTGELE.nextDouble());
			p.renk = RASTGELE.nextDouble() < 0.3 ? Color.WHITE : renk;
			p.yuvarlak = RASTGELE.nextDouble() < 0.6;
			p.don = RASTGELE.nextDouble() * 6;
			liste.add(p);
		}
	}

	static void konfeti(List<Parcacik> liste, double genislik, double hiz, double boy, double yercekimi) {
		Color[] renkler = { PEMBE, CAMGOBEGI, SARI, LIME, MOR, Color.WHITE, new Color(255, 122, 0) };
		for (int i = 0; i < 110; i++) {
			Parcacik p = new Parcacik();
			p.x = RASTGELE.nextDouble() * genislik;
			p.y = -RASTGELE.nextDouble() * boy * 12;
			p.vx = (RASTGELE.nextDouble() * 2 - 1) * hiz * 0.2;
			p.vy = hiz * (0.3 + RASTGELE.nextDouble());
			p.yercekimi = yercekimi;
			p.omur = 1.2 + RASTGELE.nextDouble();
			p.boy = boy * (0.5 + RASTGELE.nextDouble());
			p.renk = renkler[i % renkler.length];
			p.don = RASTGELE.nextDouble() * 6;
			liste.add(p);
		}
	}

	private static Image pencereIkonu() {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = hazirla(img.getGraphics());
		g.translate(2, 10);
		g.scale(60 / 200.0, 60 / 200.0);
		Cizim.oyunKolu(g);
		g.dispose();
		return img;
	}

	// =====================================================================
	// SES: Basit "blip" sesleri kodla üretilir (dosya gerekmez)
	// =====================================================================
	static final class Ses {
		static boolean kapali = false;
		private static final float ORAN = 44100f;
		private static final AudioFormat BICIM = new AudioFormat(ORAN, 16, 1, true, false);
		private static final Map<String, byte[]> ONBELLEK = new ConcurrentHashMap<>();
		private static final ExecutorService CALICI = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "ses");
			t.setDaemon(true);
			return t;
		});

		/** dalga: s=sinüs, k=kare, u=üçgen, t=testere. kayma: bitiş frekansı (0 = yok). */
		record Nota(double frekans, double baslangic, double sure, char dalga, double ses, double kayma) {
		}

		static void tik() {
			cal("tik", new Nota(880, 0, 0.05, 'k', 0.12, 0), new Nota(1320, 0.035, 0.07, 'u', 0.2, 0));
		}

		static void secim() {
			cal("secim", new Nota(660, 0, 0.09, 'u', 0.22, 990), new Nota(1320, 0.06, 0.08, 's', 0.12, 0));
		}

		static void kilitli() {
			cal("kilit", new Nota(260, 0, 0.12, 'k', 0.09, 170), new Nota(196, 0.1, 0.16, 'k', 0.08, 140));
		}

		static void donus() {
			cal("donus", new Nota(220, 0, 0.03, 's', 0.06, 0));
		}

		static void yem(int seri) {
			double taban = 480 * Math.pow(1.122, Math.min(seri, 8));
			cal("yem" + seri, new Nota(taban, 0, 0.09, 'k', 0.08, 0), new Nota(taban * 1.5, 0.06, 0.13, 'u', 0.2, 0));
		}

		static void dalga() {
			cal("dalga", new Nota(523, 0, 0.2, 'u', 0.2, 0), new Nota(659, 0.08, 0.2, 'u', 0.2, 0),
					new Nota(784, 0.16, 0.2, 'u', 0.2, 0), new Nota(1047, 0.24, 0.25, 'u', 0.2, 0));
		}

		static void bitti() {
			cal("bitti", new Nota(330, 0, 0.55, 't', 0.12, 70), new Nota(165, 0.08, 0.5, 'k', 0.06, 50));
		}

		static void kart() {
			cal("kart", new Nota(1100, 0, 0.05, 's', 0.18, 1700), new Nota(600, 0, 0.03, 'u', 0.08, 0));
		}

		static void eslesme() {
			cal("eslesme", new Nota(784, 0, 0.12, 'u', 0.2, 0), new Nota(1175, 0.08, 0.2, 'u', 0.2, 0),
					new Nota(1568, 0.16, 0.2, 's', 0.1, 0));
		}

		static void hata() {
			cal("hata", new Nota(330, 0, 0.16, 'k', 0.08, 220), new Nota(247, 0.12, 0.18, 'k', 0.07, 160));
		}

		static void zafer() {
			cal("zafer", new Nota(523, 0, 0.18, 'u', 0.2, 0), new Nota(659, 0.12, 0.18, 'u', 0.2, 0),
					new Nota(784, 0.24, 0.18, 'u', 0.2, 0), new Nota(1047, 0.36, 0.5, 'u', 0.2, 0),
					new Nota(1319, 0.36, 0.5, 's', 0.12, 0));
		}

		private static void cal(String ad, Nota... notalar) {
			if (kapali) {
				return;
			}
			byte[] veri = ONBELLEK.computeIfAbsent(ad, k -> olustur(notalar));
			CALICI.submit(() -> {
				try {
					Clip clip = AudioSystem.getClip();
					clip.open(BICIM, veri, 0, veri.length);
					clip.addLineListener(e -> {
						if (e.getType() == LineEvent.Type.STOP) {
							clip.close();
						}
					});
					clip.start();
				} catch (Exception e) {
					// Ses aygıtı yoksa oyun sessiz devam eder.
				}
			});
		}

		private static byte[] olustur(Nota[] notalar) {
			double toplamSure = 0;
			for (Nota n : notalar) {
				toplamSure = Math.max(toplamSure, n.baslangic() + n.sure());
			}
			int adet = (int) (toplamSure * ORAN) + 1;
			double[] tampon = new double[adet];
			for (Nota n : notalar) {
				int bas = (int) (n.baslangic() * ORAN);
				int uzunluk = (int) (n.sure() * ORAN);
				double faz = 0;
				for (int i = 0; i < uzunluk && bas + i < adet; i++) {
					double p = (double) i / uzunluk;
					double f = n.kayma() > 0 ? n.frekans() * Math.pow(n.kayma() / n.frekans(), p) : n.frekans();
					faz += 2 * Math.PI * f / ORAN;
					double v = switch (n.dalga()) {
					case 'k' -> Math.sin(faz) >= 0 ? 1 : -1;
					case 'u' -> 2 / Math.PI * Math.asin(Math.sin(faz));
					case 't' -> 2 * ((faz / (2 * Math.PI)) % 1) - 1;
					default -> Math.sin(faz);
					};
					double zarf = n.ses() * Math.pow(0.001, p) * Math.min(1, i / (ORAN * 0.004));
					tampon[bas + i] += v * zarf;
				}
			}
			byte[] cikti = new byte[adet * 2];
			for (int i = 0; i < adet; i++) {
				int s = (int) Math.round(sinirla(tampon[i], -1, 1) * 32000);
				cikti[2 * i] = (byte) s;
				cikti[2 * i + 1] = (byte) (s >> 8);
			}
			return cikti;
		}
	}

	// =====================================================================
	// ORTAK ARAYÜZ BİLEŞENLERİ
	// =====================================================================

	/** Neon ışıklı arka plan (gradyan, ışık küreleri, ızgara, yıldızlar). */
	static class ArkaPlanPaneli extends JPanel {
		private static final long serialVersionUID = 1L;
		private transient BufferedImage onbellek;

		ArkaPlanPaneli(LayoutManager duzen) {
			super(duzen);
			setOpaque(true);
		}

		@Override
		protected void paintComponent(Graphics g0) {
			int w = Math.max(getWidth(), 1), h = Math.max(getHeight(), 1);
			if (onbellek == null || onbellek.getWidth() != w || onbellek.getHeight() != h) {
				onbellek = arkaPlanOlustur(w, h);
			}
			g0.drawImage(onbellek, 0, 0, null);
			Graphics2D g = hazirla(g0);
			Random r = new Random(7);
			for (int i = 0; i < 70; i++) {
				double x = r.nextDouble() * w, y = r.nextDouble() * h * 0.7;
				double faz = r.nextDouble() * 6.28;
				double parlaklik = 0.3 + 0.7 * Math.pow((Math.sin(zaman * 1.6 + faz) + 1) / 2, 3);
				double s = 1 + r.nextDouble() * 2.2;
				g.setColor(alfa(Color.WHITE, (int) (parlaklik * 200)));
				g.fill(daire(x, y, s / 2));
			}
			g.dispose();
		}

		private static BufferedImage arkaPlanOlustur(int w, int h) {
			BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = hazirla(img.getGraphics());
			g.setPaint(new GradientPaint(0, 0, ARKA_UST, 0, h, ARKA_ALT));
			g.fillRect(0, 0, w, h);
			isikKuresi(g, w * 0.12, h * 0.18, h * 0.6, MOR, 80);
			isikKuresi(g, w * 0.88, h * 0.28, h * 0.62, PEMBE, 60);
			isikKuresi(g, w * 0.5, h * 1.05, h * 0.75, CAMGOBEGI, 50);
			// Alt kısımda perspektif neon ızgara
			double ufuk = h * 0.7;
			g.setStroke(new BasicStroke(1.2f));
			for (int i = 0; i < 14; i++) {
				double t = i / 13.0;
				double y = ufuk + (h - ufuk) * t * t;
				g.setColor(alfa(PEMBE, (int) (20 + 70 * t)));
				g.draw(new Line2D.Double(0, y, w, y));
			}
			for (int i = -14; i <= 14; i++) {
				g.setColor(alfa(CAMGOBEGI, 38));
				g.draw(new Line2D.Double(w / 2.0 + i * w / 70.0, ufuk, w / 2.0 + i * w / 9.0, h));
			}
			g.dispose();
			return img;
		}

		private static void isikKuresi(Graphics2D g, double cx, double cy, double r, Color c, int a) {
			g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) r, new float[] { 0f, 1f },
					new Color[] { alfa(c, a), alfa(c, 0) }));
			g.fill(daire(cx, cy, r));
		}
	}

	/** Neon renkli, yuvarlak köşeli düğme. Tıklanınca "blip" sesi çıkarır. */
	static class NeonButon extends JButton {
		private static final long serialVersionUID = 1L;
		private final Color renk1, renk2;
		private Icon ikon;

		NeonButon(String metin, Color renk1, Color renk2, float yaziBoyu) {
			super(metin);
			this.renk1 = renk1;
			this.renk2 = renk2;
			setFont(kalin(yaziBoyu));
			setForeground(Color.WHITE);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setFocusable(false);
			setOpaque(false);
			setRolloverEnabled(true);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addActionListener(e -> Ses.tik());
		}

		void ikonAyarla(Icon yeni) {
			ikon = yeni;
			revalidate();
		}

		@Override
		public Dimension getPreferredSize() {
			FontMetrics fm = getFontMetrics(getFont());
			int ikonGenisligi = ikon != null ? ikon.getIconWidth() + 10 : 0;
			int w = fm.stringWidth(getText()) + ikonGenisligi + (int) (getFont().getSize2D() * 2.4f) + 16;
			int h = (int) (getFont().getSize2D() * 2.3f) + 16;
			return new Dimension(w, h);
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = hazirla(g0);
			int w = getWidth(), h = getHeight();
			boolean uzerinde = getModel().isRollover();
			boolean basili = getModel().isPressed();
			double p = 8, y = p + (basili ? 2 : 0);
			RoundRectangle2D r = new RoundRectangle2D.Double(p, y, w - 2 * p, h - 2 * p, h - 2 * p, h - 2 * p);
			for (int i = 4; i >= 1; i--) {
				g.setColor(alfa(renk2, uzerinde ? 46 : 22));
				g.setStroke(new BasicStroke(i * 3.5f));
				g.draw(r);
			}
			g.setPaint(new GradientPaint((float) p, 0, renk1, (float) (w - p), 0, renk2));
			g.fill(r);
			g.setPaint(new GradientPaint(0, (float) y, alfa(Color.WHITE, uzerinde ? 120 : 80), 0,
					(float) (y + (h - 2 * p) * 0.55), alfa(Color.WHITE, 0)));
			g.fill(r);
			g.setColor(alfa(Color.WHITE, uzerinde ? 210 : 120));
			g.setStroke(new BasicStroke(1.5f));
			g.draw(r);

			g.setFont(getFont());
			FontMetrics fm = g.getFontMetrics();
			int ikonGenisligi = ikon != null ? ikon.getIconWidth() + 10 : 0;
			double x = (w - fm.stringWidth(getText()) - ikonGenisligi) / 2.0;
			double orta = y + (h - 2 * p) / 2.0;
			if (ikon != null) {
				ikon.paintIcon(this, g, (int) x, (int) (orta - ikon.getIconHeight() / 2.0));
			}
			double taban = orta + (fm.getAscent() - fm.getDescent()) / 2.0;
			g.setColor(new Color(0, 0, 0, 70));
			g.drawString(getText(), (float) (x + ikonGenisligi), (float) taban + 2);
			g.setColor(Color.WHITE);
			g.drawString(getText(), (float) (x + ikonGenisligi), (float) taban);
			g.dispose();
		}
	}

	/** Bütün ekranlardaki ses düğmeleri aynı anda güncellenir. */
	static class SesDugmesi extends NeonButon {
		private static final long serialVersionUID = 1L;
		private static final List<SesDugmesi> HEPSI = new ArrayList<>();

		SesDugmesi() {
			super("Ses: Açık", new Color(50, 36, 120), new Color(84, 52, 170), 14);
			HEPSI.add(this);
			addActionListener(e -> {
				Ses.kapali = !Ses.kapali;
				for (SesDugmesi d : HEPSI) {
					d.setText(Ses.kapali ? "Ses: Kapalı" : "Ses: Açık");
				}
			});
		}
	}

	/** Düğmelerdeki küçük simgeler. */
	static class SekilIkonu implements Icon {
		private final String tur;
		private final int boy;

		SekilIkonu(String tur, int boy) {
			this.tur = tur;
			this.boy = boy;
		}

		@Override
		public int getIconWidth() {
			return boy;
		}

		@Override
		public int getIconHeight() {
			return boy;
		}

		@Override
		public void paintIcon(Component c, Graphics g0, int x, int y) {
			Graphics2D g = hazirla(g0);
			g.translate(x, y);
			g.setColor(Color.WHITE);
			Path2D p = new Path2D.Double();
			if (tur.equals("oyna")) {
				p.moveTo(boy * 0.2, boy * 0.1);
				p.lineTo(boy * 0.9, boy * 0.5);
				p.lineTo(boy * 0.2, boy * 0.9);
				p.closePath();
				g.fill(p);
			} else {
				g.setStroke(new BasicStroke(boy * 0.16f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				p.moveTo(boy * 0.55, boy * 0.15);
				p.lineTo(boy * 0.2, boy * 0.5);
				p.lineTo(boy * 0.55, boy * 0.85);
				g.draw(p);
				g.draw(new Line2D.Double(boy * 0.22, boy * 0.5, boy * 0.9, boy * 0.5));
			}
			g.dispose();
		}
	}

	/** Ekranların üstündeki çubuk: geri düğmesi, ışıklı başlık, ses düğmesi. */
	static class UstBar extends JPanel {
		private static final long serialVersionUID = 1L;
		private String baslik;

		UstBar(String baslik, String geriYazisi, Runnable geri, JComponent... ekler) {
			super(new BorderLayout());
			this.baslik = baslik;
			setOpaque(false);
			setBorder(BorderFactory.createEmptyBorder(10, 18, 2, 18));
			setPreferredSize(new Dimension(100, 76));

			NeonButon geriDugmesi = new NeonButon(geriYazisi, new Color(60, 40, 140), new Color(96, 56, 190), 15);
			geriDugmesi.ikonAyarla(new SekilIkonu("geri", 16));
			geriDugmesi.addActionListener(e -> geri.run());
			JPanel sol = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			sol.setOpaque(false);
			sol.add(geriDugmesi);

			JPanel sag = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
			sag.setOpaque(false);
			for (JComponent ek : ekler) {
				sag.add(ek);
			}
			sag.add(new SesDugmesi());

			add(sol, BorderLayout.WEST);
			add(sag, BorderLayout.EAST);
		}

		void baslikAyarla(String yeni) {
			baslik = yeni;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = hazirla(g0);
			Paint dolgu = new GradientPaint(getWidth() / 2f - 160, 0, CAMGOBEGI, getWidth() / 2f + 160, 0, PEMBE);
			isikliYazi(g, baslik, kalin(30), getWidth() / 2.0, 50, dolgu, MOR, 0.9);
			g.dispose();
		}
	}

	/** Mod seçim kartı (giriş ekranında 1 kişilik / 2 kişilik). */
	static class ModKarti extends JComponent {
		private static final long serialVersionUID = 1L;
		private final int kisi;
		private boolean uzerinde;

		ModKarti(int kisi, MiniOyunMerkezi pencere) {
			this.kisi = kisi;
			setPreferredSize(new Dimension(330, 118));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					uzerinde = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e) {
					uzerinde = false;
					repaint();
				}

				@Override
				public void mousePressed(MouseEvent e) {
					pencere.modSec(kisi);
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = hazirla(g0);
			boolean secili = oyuncuSayisi == kisi;
			Color renk = kisi == 1 ? PEMBE : CAMGOBEGI;
			RoundRectangle2D r = new RoundRectangle2D.Double(8, 8, getWidth() - 16, getHeight() - 16, 26, 26);
			double nabiz = secili ? 0.8 + 0.4 * Math.sin(zaman * 3) : (uzerinde ? 0.7 : 0.25);
			neonKutu(g, r, secili ? renk : alfa(renk, 150), secili ? 200 : 150, nabiz);
			if (secili) {
				g.setPaint(new GradientPaint(0, 8, alfa(renk, 70), 0, getHeight(), alfa(renk, 0)));
				g.fill(r);
			}

			// Simge alanı
			double cx = 70, cy = getHeight() / 2.0;
			g.setColor(alfa(renk, 45));
			g.fill(daire(cx, cy, 36));
			g.setColor(alfa(renk, 200));
			g.setStroke(new BasicStroke(2f));
			g.draw(daire(cx, cy, 36));
			if (kisi == 1) {
				Cizim.insan(g, cx - 12, cy + 4, 1.0, Color.WHITE);
				Cizim.robot(g, cx + 16, cy + 6, 0.62, CAMGOBEGI);
			} else {
				Cizim.insan(g, cx - 13, cy + 4, 0.95, PEMBE);
				Cizim.insan(g, cx + 13, cy + 4, 0.95, CAMGOBEGI);
			}

			g.setFont(kalin(24));
			g.setColor(Color.WHITE);
			g.drawString(kisi == 1 ? "1 KİŞİLİK" : "2 KİŞİLİK", 124, (float) cy - 4);
			g.setFont(yazi(Font.BOLD, 14));
			g.setColor(secili ? Color.WHITE : SOLUK);
			g.drawString(kisi == 1 ? "Bilgisayara karşı oyna" : "Arkadaşınla birlikte oyna", 124,
					(float) cy + 22);

			if (secili) {
				double tx = getWidth() - 34, ty = 30;
				g.setColor(renk);
				g.fill(daire(tx, ty, 12));
				g.setColor(MUREKKEP);
				g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				Path2D tik = new Path2D.Double();
				tik.moveTo(tx - 6, ty);
				tik.lineTo(tx - 1.5, ty + 5);
				tik.lineTo(tx + 6, ty - 5);
				g.draw(tik);
			}
			g.dispose();
		}
	}

	/** Oyun merkezinin üstündeki küçük mod değiştirici. */
	static class ModSecici extends JComponent {
		private static final long serialVersionUID = 1L;

		ModSecici(MiniOyunMerkezi pencere) {
			setPreferredSize(new Dimension(280, 50));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mousePressed(MouseEvent e) {
					pencere.modSec(e.getX() < getWidth() / 2 ? 1 : 2);
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = hazirla(g0);
			double w = getWidth() - 8, h = 38, y = 6;
			RoundRectangle2D tum = new RoundRectangle2D.Double(4, y, w, h, h, h);
			g.setColor(new Color(22, 14, 58, 220));
			g.fill(tum);
			double yarim = w / 2;
			RoundRectangle2D secili = new RoundRectangle2D.Double(4 + (oyuncuSayisi == 1 ? 0 : yarim) + 3, y + 3,
					yarim - 6, h - 6, h - 6, h - 6);
			Color renk = oyuncuSayisi == 1 ? PEMBE : CAMGOBEGI;
			g.setColor(alfa(renk, 60));
			g.setStroke(new BasicStroke(6f));
			g.draw(secili);
			g.setPaint(new GradientPaint(0, (float) y, renk, 0, (float) (y + h), renk.darker()));
			g.fill(secili);
			g.setColor(alfa(Color.WHITE, 60));
			g.setStroke(new BasicStroke(1.5f));
			g.draw(tum);
			Font f = kalin(14);
			ortaYazi(g, "1 KİŞİLİK", f, 4 + yarim / 2, y + 24, oyuncuSayisi == 1 ? MUREKKEP : SOLUK);
			ortaYazi(g, "2 KİŞİLİK", f, 4 + yarim * 1.5, y + 24, oyuncuSayisi == 2 ? MUREKKEP : SOLUK);
			g.dispose();
		}
	}

	// =====================================================================
	// 1) GİRİŞ EKRANI
	// =====================================================================
	static class GirisEkrani extends ArkaPlanPaneli implements Ekran {
		private static final long serialVersionUID = 1L;
		private double birikim;

		GirisEkrani(MiniOyunMerkezi pencere) {
			super(new BorderLayout());

			JPanel ust = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 12));
			ust.setOpaque(false);
			ust.add(new SesDugmesi());
			add(ust, BorderLayout.NORTH);

			JPanel orta = new JPanel(new GridBagLayout());
			orta.setOpaque(false);
			GridBagConstraints c = new GridBagConstraints();
			c.gridx = 0;
			c.gridy = 0;
			c.insets = new Insets(0, 0, 6, 0);
			orta.add(new Logo(), c);

			NeonButon oyna = new NeonButon("OYNA", CAMGOBEGI.darker(), PEMBE, 30);
			oyna.ikonAyarla(new SekilIkonu("oyna", 26));
			oyna.setPreferredSize(new Dimension(300, 86));
			oyna.addActionListener(e -> pencere.merkezeGit());
			c.gridy++;
			c.insets = new Insets(4, 0, 18, 0);
			orta.add(oyna, c);

			JLabel modYazisi = new JLabel("OYUN MODUNU SEÇ");
			modYazisi.setFont(kalin(14));
			modYazisi.setForeground(SOLUK);
			c.gridy++;
			c.insets = new Insets(0, 0, 4, 0);
			orta.add(modYazisi, c);

			JPanel modlar = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
			modlar.setOpaque(false);
			modlar.add(new ModKarti(1, pencere));
			modlar.add(new ModKarti(2, pencere));
			c.gridy++;
			orta.add(modlar, c);

			JLabel not = new JLabel("İki mod da aynı oyun merkezini açar. Modu merkezde de değiştirebilirsin.");
			not.setFont(yazi(Font.PLAIN, 13));
			not.setForeground(alfa(SOLUK, 200));
			c.gridy++;
			c.insets = new Insets(12, 0, 0, 0);
			orta.add(not, c);

			add(orta, BorderLayout.CENTER);
		}

		@Override
		public void guncelle(double dt) {
			birikim += dt;
			if (birikim > 1 / 30.0) {
				birikim = 0;
				repaint();
			}
		}
	}

	/** Giriş ekranındaki büyük logo. */
	static class Logo extends JComponent {
		private static final long serialVersionUID = 1L;

		Logo() {
			setPreferredSize(new Dimension(940, 330));
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = hazirla(g0);
			double w = getWidth(), cx = w / 2;
			double nabiz = 0.85 + 0.25 * Math.sin(zaman * 2.2);

			// Arkadaki ışık halesi (yassı, kenarları yumuşak)
			Graphics2D hale = (Graphics2D) g.create();
			hale.translate(cx, 160);
			hale.scale(1, 0.44);
			hale.setPaint(new RadialGradientPaint(new Point2D.Double(0, 0), 360, new float[] { 0f, 1f },
					new Color[] { alfa(MOR, (int) (110 * nabiz)), alfa(MOR, 0) }));
			hale.fill(daire(0, 0, 360));
			hale.dispose();

			// Solda yılan maskotu
			Graphics2D m = (Graphics2D) g.create();
			m.translate(cx - 445, 92 + Math.sin(zaman * 2) * 4);
			m.rotate(-0.06, 110, 100);
			m.scale(0.72, 0.72);
			Cizim.maskot(m, YESIL_YILAN);
			m.dispose();

			// Sağda yelpaze gibi hafıza kartları
			int[] kartlar = { 9, 10, 20 };
			for (int i = 0; i < 3; i++) {
				Graphics2D k = (Graphics2D) g.create();
				k.translate(cx + 330 + i * 30, 196 + Math.abs(i - 1) * 8);
				k.rotate((i - 1) * 0.28 + Math.sin(zaman * 1.5 + i) * 0.03);
				Cizim.kucukKart(k, kartlar[i], 82, 106);
				k.dispose();
			}

			// Ortada oyun kolu
			Graphics2D kol = (Graphics2D) g.create();
			kol.translate(cx - 100, 8 + Math.sin(zaman * 2.4) * 3);
			Cizim.oyunKolu(kol);
			kol.dispose();

			// Yazı
			Paint dolgu = new GradientPaint((float) (cx - 260), 0, CAMGOBEGI, (float) (cx + 260), 0, PEMBE);
			isikliYazi(g, "MİNİ OYUN", kalin(78), cx, 222, dolgu, PEMBE, nabiz);
			isikliYazi(g, "M E R K E Z İ", kalin(34), cx, 272, SARI, new Color(255, 150, 0), nabiz * 0.9);

			// Parıltılar
			double[][] yildizlar = { { cx - 300, 60 }, { cx + 270, 40 }, { cx - 250, 250 }, { cx + 230, 290 },
					{ cx + 20, 300 } };
			for (int i = 0; i < yildizlar.length; i++) {
				double s = 6 + 6 * (Math.sin(zaman * 3 + i * 1.7) + 1) / 2;
				Cizim.parilti(g, yildizlar[i][0], yildizlar[i][1], s);
			}
			g.dispose();
		}
	}

	// =====================================================================
	// 2) OYUN MERKEZİ (10 oyun)
	// =====================================================================
	record OyunBilgisi(String ad, String aciklama, String ikon, Color renk1, Color renk2, boolean oynanabilir) {
	}

	static final OyunBilgisi[] OYUNLAR = {
			new OyunBilgisi("Yılan", "Yemleri topla, büyü!", "yilan", new Color(0x2bd36b), new Color(0x0b7a3a), true),
			new OyunBilgisi("Hafıza Kartları", "Eşleri bul, hafızanı sına", "hafiza", new Color(0xb14bff),
					new Color(0xff3fa4), true),
			new OyunBilgisi("Uzay Savaşı", "Göktaşlarını vur", "roket", new Color(0x1e3cff), new Color(0x00c8ff),
					false),
			new OyunBilgisi("Tuğla Kırıcı", "Topla tuğlaları kır", "tugla", new Color(0xff7a18), new Color(0xffc300),
					false),
			new OyunBilgisi("Balon Patlat", "Hiçbir balonu kaçırma", "balon", new Color(0xff3d6e),
					new Color(0xff9a8b), false),
			new OyunBilgisi("Labirent", "Çıkışı en hızlı bul", "labirent", new Color(0x00a383), new Color(0x2ee6c5),
					false),
			new OyunBilgisi("Blok Yığma", "Satırları tamamla", "blok", new Color(0x5b4bdb), new Color(0xa29bfe),
					false),
			new OyunBilgisi("Penaltı Ustası", "Kaleciyi geç, gol at", "futbol", new Color(0x1e9e4f),
					new Color(0x8be28b), false),
			new OyunBilgisi("XOX", "Üçünü yan yana diz", "xox", new Color(0x0c6fd6), new Color(0x6fc3ff), false),
			new OyunBilgisi("Araba Yarışı", "Virajlarda hızını koru", "araba", new Color(0xd62f45),
					new Color(0xff8a5c), false) };

	static class MerkezEkrani extends ArkaPlanPaneli implements Ekran {
		private static final long serialVersionUID = 1L;
		private double birikim;

		MerkezEkrani(MiniOyunMerkezi pencere) {
			super(new BorderLayout());
			add(new UstBar("OYUN MERKEZİ", "Giriş", pencere::girisEkraninaGit, new ModSecici(pencere)),
					BorderLayout.NORTH);

			JPanel izgara = new JPanel(new GridLayout(2, 5, 16, 16));
			izgara.setOpaque(false);
			izgara.setBorder(BorderFactory.createEmptyBorder(14, 26, 8, 26));
			for (OyunBilgisi oyun : OYUNLAR) {
				Runnable ac;
				if (oyun.ikon().equals("yilan")) {
					ac = pencere::yilanAc;
				} else if (oyun.ikon().equals("hafiza")) {
					ac = pencere::hafizaAc;
				} else {
					ac = () -> JOptionPane.showMessageDialog(pencere,
							oyun.ad() + " henüz hazır değil.\nÇok yakında Mini Oyun Merkezi'nde!", "Yakında",
							JOptionPane.INFORMATION_MESSAGE);
				}
				izgara.add(new OyunKarti(oyun, ac));
			}
			add(izgara, BorderLayout.CENTER);

			JLabel alt = new JLabel("2 oyun oynanabilir  •  8 oyun yakında  •  Seçili mod tüm oyunlara uygulanır",
					SwingConstants.CENTER);
			alt.setFont(yazi(Font.BOLD, 13));
			alt.setForeground(SOLUK);
			alt.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
			add(alt, BorderLayout.SOUTH);
		}

		@Override
		public void guncelle(double dt) {
			birikim += dt;
			if (birikim > 1 / 20.0) {
				birikim = 0;
				repaint();
			}
		}
	}

	/** Merkezdeki tek bir oyun kartı. */
	static class OyunKarti extends JComponent {
		private static final long serialVersionUID = 1L;
		private final OyunBilgisi oyun;
		private boolean uzerinde;

		OyunKarti(OyunBilgisi oyun, Runnable ac) {
			this.oyun = oyun;
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					uzerinde = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e) {
					uzerinde = false;
					repaint();
				}

				@Override
				public void mouseClicked(MouseEvent e) {
					if (oyun.oynanabilir()) {
						Ses.tik();
					} else {
						Ses.kilitli();
					}
					ac.run();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g0) {
			Graphics2D g = hazirla(g0);
			double kalkma = uzerinde ? -6 : 0;
			RoundRectangle2D r = new RoundRectangle2D.Double(8, 10 + kalkma, getWidth() - 16, getHeight() - 22, 24,
					24);
			double ustYukseklik = r.getHeight() * 0.58;

			if (uzerinde) {
				for (int i = 4; i >= 1; i--) {
					g.setColor(alfa(oyun.renk1(), 60 / i + 10));
					g.setStroke(new BasicStroke(i * 5f));
					g.draw(r);
				}
			}
			g.setColor(new Color(20, 14, 52, 240));
			g.fill(r);

			// Üst görsel alanı
			Graphics2D ust = (Graphics2D) g.create();
			ust.clip(r);
			ust.setPaint(new GradientPaint((float) r.getX(), (float) r.getY(), oyun.renk1(), (float) r.getMaxX(),
					(float) (r.getY() + ustYukseklik), oyun.renk2()));
			ust.fill(new Rectangle2D.Double(r.getX(), r.getY(), r.getWidth(), ustYukseklik));
			ust.setColor(alfa(Color.WHITE, 30));
			ust.fill(daire(r.getX() + r.getWidth() * 0.85, r.getY() + 10, r.getWidth() * 0.4));
			ust.fill(daire(r.getX() + 6, r.getY() + ustYukseklik, r.getWidth() * 0.25));

			double boy = Math.min(r.getWidth() * 0.66, ustYukseklik * 0.84);
			double nabiz = uzerinde ? 1 + 0.04 * Math.sin(zaman * 6) : 1;
			Graphics2D ik = (Graphics2D) ust.create();
			ik.translate(r.getCenterX(), r.getY() + ustYukseklik / 2 + 4);
			ik.scale(boy / 100 * nabiz, boy / 100 * nabiz);
			ik.translate(-50, -50);
			Cizim.oyunIkonu(ik, oyun.ikon());
			ik.dispose();

			if (!oyun.oynanabilir()) {
				ust.setColor(new Color(10, 6, 30, 120));
				ust.fill(new Rectangle2D.Double(r.getX(), r.getY(), r.getWidth(), ustYukseklik));
				Cizim.kilit(ust, r.getMaxX() - 30, r.getY() + 30, 1.0);
			}
			ust.dispose();

			// Rozet
			String rozet = oyun.oynanabilir() ? "OYNANABİLİR" : "YAKINDA";
			Font rf = kalin(11);
			FontMetrics fm = g.getFontMetrics(rf);
			RoundRectangle2D rozetAlani = new RoundRectangle2D.Double(r.getX() + 12, r.getY() + 12,
					fm.stringWidth(rozet) + 18, 22, 22, 22);
			g.setColor(oyun.oynanabilir() ? LIME : new Color(60, 54, 90, 230));
			g.fill(rozetAlani);
			g.setFont(rf);
			g.setColor(oyun.oynanabilir() ? MUREKKEP : new Color(210, 205, 235));
			g.drawString(rozet, (float) (r.getX() + 21), (float) (r.getY() + 27));

			// Ad ve açıklama
			double ty = r.getY() + ustYukseklik + 30;
			g.setFont(kalin(18));
			g.setColor(Color.WHITE);
			g.drawString(oyun.ad(), (float) (r.getX() + 14), (float) ty);
			g.setFont(yazi(Font.PLAIN, 13));
			g.setColor(SOLUK);
			g.drawString(oyun.aciklama(), (float) (r.getX() + 14), (float) ty + 20);

			// Alt düğme
			double dy = r.getMaxY() - 42;
			if (oyun.oynanabilir()) {
				Rectangle2D d = new Rectangle2D.Double(r.getX() + 14, dy, r.getWidth() - 28, 32);
				cizilmisDugme(g, d, "OYNA", CAMGOBEGI.darker(), PEMBE, uzerinde);
			} else {
				Cizim.kilit(g, r.getX() + 26, dy + 16, 0.6);
				g.setFont(kalin(13));
				g.setColor(alfa(SOLUK, 200));
				g.drawString("KİLİTLİ", (float) (r.getX() + 42), (float) dy + 21);
			}

			g.setColor(alfa(Color.WHITE, uzerinde ? 110 : 40));
			g.setStroke(new BasicStroke(1.5f));
			g.draw(r);
			g.dispose();
		}
	}

	// =====================================================================
	// 3) YILAN OYUNU
	// =====================================================================
	record Nokta(int x, int y) {
	}

	static final class YilanOyunu extends ArkaPlanPaneli implements Ekran {
		private static final long serialVersionUID = 1L;

		enum Durum {
			HAZIR, OYNUYOR, DURAKLADI, OLUYOR, BITTI
		}

		static final Nokta YUKARI = new Nokta(0, -1), ASAGI = new Nokta(0, 1), SOL = new Nokta(-1, 0),
				SAG = new Nokta(1, 0);
		// Yem türleri: 0 = elma, 1 = üzüm, 2 = elmas
		static final int[] YEM_PUAN = { 10, 20, 50 };
		static final int[] YEM_AGIRLIK = { 6, 3, 1 };
		static final int[] YEM_GORSEL = { 0, 5, 20 };
		static final Color[] YEM_RENK = { new Color(0xff2d3f), new Color(0x8a2be2), new Color(0x18d6f0) };
		static final double SERI_SURESI = 5;

		static final class Yilan {
			final Palet palet;
			final List<Nokta> govde = new ArrayList<>();
			List<Nokta> onceki = new ArrayList<>();
			final ArrayDeque<Nokta> sira = new ArrayDeque<>();
			final List<double[]> yudumlar = new ArrayList<>();
			Nokta yon;
			int skor, seri;
			double seriKalan, aci, sonrakiKirpma = 2, kirpmaBitis, dilFaz = RASTGELE.nextDouble() * 6;
			boolean olu;

			Yilan(Palet palet) {
				this.palet = palet;
			}
		}

		static final class Yem {
			int x, y, tur;
			double dogus, faz;
		}

		static final class UcanYazi {
			double x, y, zaman, boy;
			String metin;
			Color renk;
		}

		private final MiniOyunMerkezi pencere;
		private final UstBar ustBar;
		private final Tuval tuval = new Tuval();
		private final List<Yilan> yilanlar = new ArrayList<>();
		private final List<Yem> yemler = new ArrayList<>();
		private final List<Parcacik> parcaciklar = new ArrayList<>();
		private final List<UcanYazi> yazilar = new ArrayList<>();
		private List<DugmeAlani> dugmeler = new ArrayList<>();
		private Durum durum = Durum.HAZIR;
		private int kisi = 1, sutun = 22, satir = 16, dalga, dalgaBoyu, baslangicEnIyi;
		private static int enIyi = 0;
		private double tikMs = 135, birikim, alfaDegeri = 1, t, bitis, sarsinti, flas, bannerZaman = -10;
		private String bannerYazisi = "", sonucBaslik = "", sonucAlt = "";
		private Color sonucRenk = PEMBE;
		private Point fare;
		private transient BufferedImage tahtaOnbellek;
		private double tahtaHucre;

		YilanOyunu(MiniOyunMerkezi pencere) {
			super(new BorderLayout());
			this.pencere = pencere;
			ustBar = new UstBar("YILAN", "Merkez", this::merkezeDon);
			add(ustBar, BorderLayout.NORTH);
			add(tuval, BorderLayout.CENTER);
			yeniOyun(1);
		}

		void odakla() {
			SwingUtilities.invokeLater(tuval::requestFocusInWindow);
		}

		private void merkezeDon() {
			if (durum == Durum.OYNUYOR) {
				durum = Durum.DURAKLADI;
			}
			pencere.merkezeGit();
		}

		void duraklat() {
			if (durum == Durum.OYNUYOR) {
				durum = Durum.DURAKLADI;
			}
		}

		void yeniOyun(int kisiSayisi) {
			kisi = kisiSayisi;
			sutun = kisi == 1 ? 22 : 26;
			satir = 16;
			yilanlar.clear();
			yemler.clear();
			parcaciklar.clear();
			yazilar.clear();
			if (kisi == 1) {
				yilanlar.add(yilanOlustur(YESIL_YILAN, sutun / 3, satir / 2, SAG));
			} else {
				yilanlar.add(yilanOlustur(YESIL_YILAN, 5, satir / 3, SAG));
				yilanlar.add(yilanOlustur(MAVI_YILAN, sutun - 6, satir * 2 / 3, SOL));
			}
			dalga = 0;
			sarsinti = flas = 0;
			alfaDegeri = 1;
			birikim = 0;
			baslangicEnIyi = enIyi;
			durum = Durum.HAZIR;
			sonrakiDalga();
			bannerZaman = -10;
			ustBar.baslikAyarla(kisi == 1 ? "YILAN  •  TEK KİŞİLİK" : "YILAN  •  2 KİŞİLİK");
		}

		private Yilan yilanOlustur(Palet palet, int x, int y, Nokta yon) {
			Yilan yilan = new Yilan(palet);
			for (int i = 0; i < 4; i++) {
				yilan.govde.add(new Nokta(x - yon.x() * i, y - yon.y() * i));
			}
			yilan.onceki = new ArrayList<>(yilan.govde);
			yilan.yon = yon;
			yilan.aci = Math.atan2(yon.y(), yon.x());
			return yilan;
		}

		private void basla() {
			durum = Durum.OYNUYOR;
			birikim = 0;
			bannerZaman = t;
			Ses.tik();
		}

		private void yeniden() {
			yeniOyun(kisi);
			basla();
		}

		// ---------------- Oyun mantığı ----------------
		@Override
		public void guncelle(double dt) {
			t += dt;
			if (durum == Durum.OYNUYOR) {
				birikim += dt * 1000;
				while (birikim >= tikMs && durum == Durum.OYNUYOR) {
					birikim -= tikMs;
					adim();
				}
				if (durum == Durum.OYNUYOR) {
					alfaDegeri = birikim / tikMs;
				}
				for (Yilan y : yilanlar) {
					if (y.seriKalan > 0) {
						y.seriKalan -= dt;
						if (y.seriKalan <= 0) {
							y.seriKalan = 0;
							y.seri = 0;
						}
					}
					double hedef = Math.atan2(y.yon.y(), y.yon.x());
					double fark = Math.atan2(Math.sin(hedef - y.aci), Math.cos(hedef - y.aci));
					y.aci += fark * Math.min(1, dt * 22);
					double yudumHizi = 1000 / tikMs * 2.4;
					for (double[] yudum : y.yudumlar) {
						yudum[0] += yudumHizi * dt;
					}
					y.yudumlar.removeIf(yudum -> yudum[0] > y.govde.size() + 2);
				}
			}
			if (durum != Durum.DURAKLADI) {
				for (Yilan y : yilanlar) {
					if (t > y.sonrakiKirpma) {
						y.kirpmaBitis = t + 0.13;
						y.sonrakiKirpma = t + 1.8 + RASTGELE.nextDouble() * 2.7;
					}
				}
				if (durum == Durum.OLUYOR && t >= bitis) {
					durum = Durum.BITTI;
				}
				parcaciklar.removeIf(p -> !p.guncelle(dt));
				yazilar.removeIf(y -> t - y.zaman > 0.9);
				sarsinti = Math.max(0, sarsinti - dt);
				flas = Math.max(0, flas - dt);
			}
			tuval.repaint();
		}

		/** Bir adım: bütün yılanlar aynı anda bir kare ilerler. */
		private void adim() {
			int n = yilanlar.size();
			Nokta[] yeniBas = new Nokta[n];
			Yem[] yenen = new Yem[n];
			for (int i = 0; i < n; i++) {
				Yilan y = yilanlar.get(i);
				if (!y.sira.isEmpty()) {
					y.yon = y.sira.poll();
				}
				Nokta bas = y.govde.get(0);
				yeniBas[i] = new Nokta(bas.x() + y.yon.x(), bas.y() + y.yon.y());
				yenen[i] = yemBul(yeniBas[i]);
			}

			boolean carpismaVar = false;
			for (int i = 0; i < n; i++) {
				Nokta h = yeniBas[i];
				boolean carpti = h.x() < 0 || h.y() < 0 || h.x() >= sutun || h.y() >= satir;
				for (int j = 0; j < n && !carpti; j++) {
					List<Nokta> govde = yilanlar.get(j).govde;
					// Yem yemeyen yılanın kuyruğu bu adımda boşalır, oraya girmek serbest.
					int son = yenen[j] != null ? govde.size() : govde.size() - 1;
					for (int k = 0; k < son; k++) {
						if (govde.get(k).equals(h)) {
							carpti = true;
							break;
						}
					}
					if (j != i && yeniBas[j].equals(h)) {
						carpti = true; // Kafa kafaya çarpışma
					}
				}
				if (carpti) {
					Yilan y = yilanlar.get(i);
					y.olu = true;
					Nokta b = y.govde.get(0);
					patlama(parcaciklar, b.x() + 0.5, b.y() + 0.5, y.palet.ana(), 26, 7, 0.16, 10);
					carpismaVar = true;
				}
			}
			if (carpismaVar) {
				oyunBitti();
				return;
			}

			for (int i = 0; i < n; i++) {
				Yilan y = yilanlar.get(i);
				y.onceki = new ArrayList<>(y.govde);
				y.govde.add(0, yeniBas[i]);
				if (yenen[i] != null) {
					y.onceki.add(y.onceki.get(y.onceki.size() - 1));
				} else {
					y.govde.remove(y.govde.size() - 1);
				}
			}
			for (int i = 0; i < n; i++) {
				if (yenen[i] != null) {
					yemiYe(yilanlar.get(i), yenen[i]);
				}
			}
		}

		private Yem yemBul(Nokta h) {
			for (Yem yem : yemler) {
				if (yem.x == h.x() && yem.y == h.y()) {
					return yem;
				}
			}
			return null;
		}

		private void yemiYe(Yilan y, Yem yem) {
			yemler.remove(yem);
			y.seri = y.seriKalan > 0 ? Math.min(y.seri + 1, 5) : 1;
			y.seriKalan = SERI_SURESI;
			int kazanc = YEM_PUAN[yem.tur] * y.seri;
			puanEkle(y, kazanc);
			patlama(parcaciklar, yem.x + 0.5, yem.y + 0.5, YEM_RENK[yem.tur], 16, 6, 0.14, 10);
			yaziEkle(yem.x + 0.5, yem.y + 0.5, "+" + kazanc, y.seri > 1 ? SARI : Color.WHITE, y.seri > 1 ? 1 : 0.8);
			y.yudumlar.add(new double[] { 0 });
			Ses.yem(y.seri);

			// Tahtadaki yemler bittiyse yenileri otomatik gelir.
			if (yemler.isEmpty()) {
				int bonus = 25 * dalga;
				puanEkle(y, bonus);
				yaziEkle(sutun / 2.0, satir / 2.0 + 3, "Temizlendi +" + bonus, PEMBE, 1.1);
				konfeti(parcaciklar, sutun, 9, 0.24, 5);
				Ses.dalga();
				sonrakiDalga();
			}
		}

		private void puanEkle(Yilan y, int miktar) {
			y.skor += miktar;
			if (kisi == 1 && y.skor > enIyi) {
				enIyi = y.skor;
			}
		}

		private void yaziEkle(double x, double y, String metin, Color renk, double boy) {
			UcanYazi u = new UcanYazi();
			u.x = x;
			u.y = y;
			u.metin = metin;
			u.renk = renk;
			u.boy = boy;
			u.zaman = t;
			yazilar.add(u);
		}

		private void sonrakiDalga() {
			dalga++;
			tikMs = Math.max(62, 135 - (dalga - 1) * 5);
			List<Nokta> bos = new ArrayList<>();
			for (int y = 0; y < satir; y++) {
				for (int x = 0; x < sutun; x++) {
					Nokta n = new Nokta(x, y);
					boolean dolu = yemBul(n) != null;
					for (Yilan yilan : yilanlar) {
						dolu |= yilan.govde.contains(n);
					}
					if (!dolu) {
						bos.add(n);
					}
				}
			}
			if (bos.isEmpty()) {
				oyunBitti();
				return;
			}
			dalgaBoyu = Math.min(Math.min(2 + dalga + (kisi - 1), 10), bos.size());
			// Yem, yılanların burnunun dibinde belirmesin (yer varsa).
			List<Nokta> uzak = new ArrayList<>();
			for (Nokta n : bos) {
				boolean yakin = false;
				for (Yilan yilan : yilanlar) {
					Nokta b = yilan.govde.get(0);
					yakin |= Math.abs(n.x() - b.x()) + Math.abs(n.y() - b.y()) <= 2;
				}
				if (!yakin) {
					uzak.add(n);
				}
			}
			List<Nokta> havuz = uzak.size() >= dalgaBoyu ? uzak : bos;
			for (int i = 0; i < dalgaBoyu; i++) {
				Nokta n = havuz.remove(RASTGELE.nextInt(havuz.size()));
				Yem yem = new Yem();
				yem.x = n.x();
				yem.y = n.y();
				yem.tur = yemTuruSec();
				yem.dogus = t + i * 0.07;
				yem.faz = RASTGELE.nextDouble() * 6;
				yemler.add(yem);
			}
			bannerYazisi = "DALGA " + dalga;
			bannerZaman = t;
		}

		private int yemTuruSec() {
			int toplam = 0;
			for (int a : YEM_AGIRLIK) {
				toplam += a;
			}
			int zar = RASTGELE.nextInt(toplam);
			for (int i = 0; i < YEM_AGIRLIK.length; i++) {
				zar -= YEM_AGIRLIK[i];
				if (zar < 0) {
					return i;
				}
			}
			return 0;
		}

		private void oyunBitti() {
			durum = Durum.OLUYOR;
			bitis = t + 1.0;
			sarsinti = 0.5;
			flas = 0.25;
			alfaDegeri = 1;
			Ses.bitti();
			for (Yilan y : yilanlar) {
				y.seri = 0;
				y.seriKalan = 0;
			}
			if (kisi == 1) {
				Yilan y = yilanlar.get(0);
				boolean rekor = y.skor > baslangicEnIyi && y.skor > 0;
				sonucBaslik = rekor ? "YENİ REKOR!" : "OYUN BİTTİ";
				sonucRenk = rekor ? SARI : PEMBE;
				sonucAlt = "Skor: " + y.skor + "    •    Dalga: " + dalga + "    •    En iyi: " + enIyi;
			} else {
				Yilan a = yilanlar.get(0), b = yilanlar.get(1);
				Yilan kazanan;
				String neden;
				if (a.olu && b.olu) {
					kazanan = a.skor == b.skor ? null : (a.skor > b.skor ? a : b);
					neden = "İki yılan da çarptı";
				} else {
					kazanan = a.olu ? b : a;
					neden = (a.olu ? "Yeşil" : "Mavi") + " yılan çarptı";
				}
				sonucBaslik = kazanan == null ? "BERABERE!" : kazanan.palet.ad() + " KAZANDI!";
				sonucRenk = kazanan == null ? SARI : kazanan.palet.acik();
				sonucAlt = neden + "    •    Yeşil: " + a.skor + "    Mavi: " + b.skor;
			}
		}

		private void yonVer(Yilan y, Nokta yon) {
			if (y.olu) {
				return;
			}
			Nokta son = y.sira.isEmpty() ? y.yon : y.sira.peekLast();
			if (yon.equals(son) || (yon.x() == -son.x() && yon.y() == -son.y())) {
				return;
			}
			if (y.sira.size() < 2) {
				y.sira.add(yon);
				Ses.donus();
			}
		}

		/** Yön tuşu ise yılanı çevirir ve true döner. */
		private boolean yonTusu(int k) {
			Nokta wasd = switch (k) {
			case KeyEvent.VK_W -> YUKARI;
			case KeyEvent.VK_S -> ASAGI;
			case KeyEvent.VK_A -> SOL;
			case KeyEvent.VK_D -> SAG;
			default -> null;
			};
			Nokta ok = switch (k) {
			case KeyEvent.VK_UP -> YUKARI;
			case KeyEvent.VK_DOWN -> ASAGI;
			case KeyEvent.VK_LEFT -> SOL;
			case KeyEvent.VK_RIGHT -> SAG;
			default -> null;
			};
			if (kisi == 1) {
				Nokta yon = wasd != null ? wasd : ok;
				if (yon != null) {
					yonVer(yilanlar.get(0), yon);
					return true;
				}
				return false;
			}
			if (wasd != null) {
				yonVer(yilanlar.get(0), wasd);
				return true;
			}
			if (ok != null) {
				yonVer(yilanlar.get(1), ok);
				return true;
			}
			return false;
		}

		private void tusaBasildi(int k) {
			switch (durum) {
			case HAZIR -> {
				if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER) {
					basla();
				} else if (k == KeyEvent.VK_ESCAPE) {
					merkezeDon();
				} else if (yonTusu(k)) {
					basla();
				}
			}
			case OYNUYOR -> {
				if (!yonTusu(k) && (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_P || k == KeyEvent.VK_ESCAPE)) {
					durum = Durum.DURAKLADI;
					Ses.tik();
				}
			}
			case DURAKLADI -> {
				if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_P || k == KeyEvent.VK_ENTER) {
					durum = Durum.OYNUYOR;
					Ses.tik();
				} else if (k == KeyEvent.VK_ESCAPE) {
					merkezeDon();
				}
			}
			case BITTI -> {
				if (k == KeyEvent.VK_SPACE || k == KeyEvent.VK_ENTER) {
					yeniden();
				} else if (k == KeyEvent.VK_ESCAPE) {
					merkezeDon();
				}
			}
			default -> {
			}
			}
		}

		// ---------------- Çizim ----------------
		final class Tuval extends JComponent {
			private static final long serialVersionUID = 1L;

			Tuval() {
				setOpaque(false);
				setFocusable(true);
				setFocusTraversalKeysEnabled(false);
				addKeyListener(new KeyAdapter() {
					@Override
					public void keyPressed(KeyEvent e) {
						tusaBasildi(e.getKeyCode());
					}
				});
				MouseAdapter fareDinleyici = new MouseAdapter() {
					@Override
					public void mousePressed(MouseEvent e) {
						requestFocusInWindow();
						for (DugmeAlani d : dugmeler) {
							if (d.alan().contains(e.getPoint())) {
								Ses.tik();
								d.is().run();
								return;
							}
						}
					}

					@Override
					public void mouseMoved(MouseEvent e) {
						fare = e.getPoint();
					}
				};
				addMouseListener(fareDinleyici);
				addMouseMotionListener(fareDinleyici);
			}

			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = hazirla(g0);
				ciz(g, getWidth(), getHeight());
				g.dispose();
			}
		}

		private void ciz(Graphics2D g, int W, int H) {
			hudCiz(g, W, 4, 64);
			double ust = 84, alt = 18;
			double hucre = Math.floor(Math.min((W - 48) / (double) sutun, (H - ust - alt) / (double) satir));
			if (hucre < 4) {
				return;
			}
			double bw = hucre * sutun, bh = hucre * satir;
			double ox = Math.round((W - bw) / 2), oy = Math.round(ust + (H - ust - alt - bh) / 2);

			// Tahtanın neon çerçevesi
			RoundRectangle2D cerceve = new RoundRectangle2D.Double(ox - 7, oy - 7, bw + 14, bh + 14, 28, 28);
			neonKutu(g, cerceve, kisi == 1 ? LIME : CAMGOBEGI, 255, 1.1);

			double sx = 0, sy = 0;
			if (sarsinti > 0) {
				sx = (RASTGELE.nextDouble() * 2 - 1) * sarsinti * 0.7;
				sy = (RASTGELE.nextDouble() * 2 - 1) * sarsinti * 0.7;
			}
			Graphics2D tg = (Graphics2D) g.create();
			tg.clip(new RoundRectangle2D.Double(ox, oy, bw, bh, 18, 18));
			tg.drawImage(tahtaResmi((int) bw, (int) bh, hucre), (int) Math.round(ox + sx * hucre),
					(int) Math.round(oy + sy * hucre), null);
			tg.translate(ox + sx * hucre, oy + sy * hucre);
			tg.scale(hucre, hucre);
			yemleriCiz(tg);
			for (Yilan y : yilanlar) {
				yilanCiz(tg, y);
			}
			for (Parcacik p : parcaciklar) {
				p.ciz(tg);
			}
			if (flas > 0) {
				tg.setColor(new Color(255, 46, 147, (int) Math.min(255, flas * 1.6 * 255)));
				tg.fill(new Rectangle2D.Double(-1, -1, sutun + 2, satir + 2));
			}
			tg.dispose();

			// Uçan puan yazıları ve dalga yazısı
			for (UcanYazi u : yazilar) {
				double yas = t - u.zaman, k = yas / 0.9;
				double buyume = yas < 0.15 ? geriYaylan(yas / 0.15) : 1;
				konturluYazi(g, u.metin, kalin((float) (u.boy * hucre * 0.95)), ox + u.x * hucre,
						oy + (u.y - k * 1.3) * hucre, u.renk, (1 - k) * 2.5, buyume);
			}
			double bannerYas = t - bannerZaman;
			if (durum != Durum.HAZIR && bannerYas < 1.2) {
				double buyume = bannerYas < 0.25 ? geriYaylan(bannerYas / 0.25) : 1;
				double saydam = bannerYas > 0.85 ? (1.2 - bannerYas) / 0.35 : 1;
				konturluYazi(g, bannerYazisi, kalin((float) (hucre * 1.8)), ox + bw / 2, oy + bh / 2, Color.WHITE,
						saydam, buyume);
			}

			// Bilgi paneli
			dugmeler = new ArrayList<>();
			if (durum == Durum.HAZIR) {
				String[] satirlar = kisi == 1
						? new String[] { "Yön tuşları veya W A S D ile yönlendir.",
								"Yemleri ye; tahta temizlenince yeni dalga gelir!" }
						: new String[] { "YEŞİL: W A S D      •      MAVİ: Yön tuşları",
								"Önce çarpan kaybeder. Yemlerden puan topla!" };
				dugmeler = bilgiPaneli(g, W, H, kisi == 1 ? "YILAN" : "YILAN DÜELLOSU", LIME, satirlar,
						new String[] { "BAŞLA", "MERKEZ" }, new Runnable[] { this::basla, this::merkezeDon }, fare,
						this::maskotlariCiz);
			} else if (durum == Durum.DURAKLADI) {
				dugmeler = bilgiPaneli(g, W, H, "DURAKLATILDI", CAMGOBEGI,
						new String[] { "Devam etmek için BOŞLUK tuşuna bas." }, new String[] { "DEVAM", "MERKEZ" },
						new Runnable[] { () -> durum = Durum.OYNUYOR, this::merkezeDon }, fare, null);
			} else if (durum == Durum.BITTI) {
				dugmeler = bilgiPaneli(g, W, H, sonucBaslik, sonucRenk, new String[] { sonucAlt },
						new String[] { "TEKRAR OYNA", "MERKEZ" }, new Runnable[] { this::yeniden, this::merkezeDon },
						fare, this::maskotlariCiz);
			}
		}

		private void maskotlariCiz(Graphics2D g, Rectangle2D alan) {
			double boy = alan.getHeight();
			double olcek = boy / 290.0;
			int adet = yilanlar.size();
			for (int i = 0; i < adet; i++) {
				Graphics2D m = (Graphics2D) g.create();
				double genislik = 320 * olcek;
				double x = alan.getCenterX() - adet * genislik / 2 + i * genislik;
				m.translate(x, alan.getY() + Math.sin(t * 2 + i) * 3);
				m.scale(olcek, olcek);
				Cizim.maskot(m, yilanlar.get(i).palet);
				m.dispose();
			}
		}

		private void hudCiz(Graphics2D g, int W, double y0, double h) {
			double aralik = 14;
			if (kisi == 1) {
				Yilan y = yilanlar.get(0);
				double[] gen = { 170, 140, 120, 220, 170 };
				double x = (W - (Arrays.stream(gen).sum() + aralik * 4)) / 2;
				hudKutusu(g, x, y0, gen[0], h, "SKOR", String.valueOf(y.skor), LIME);
				x += gen[0] + aralik;
				hudKutusu(g, x, y0, gen[1], h, "EN İYİ", String.valueOf(enIyi), SARI);
				x += gen[1] + aralik;
				hudKutusu(g, x, y0, gen[2], h, "DALGA", String.valueOf(dalga), CAMGOBEGI);
				x += gen[2] + aralik;
				yemKutusu(g, x, y0, gen[3], h);
				x += gen[3] + aralik;
				seriKutusu(g, x, y0, gen[4], h, y, MOR);
			} else {
				Yilan a = yilanlar.get(0), b = yilanlar.get(1);
				double[] gen = { 230, 120, 220, 230 };
				double x = (W - (Arrays.stream(gen).sum() + aralik * 3)) / 2;
				oyuncuSkoru(g, x, y0, gen[0], h, a);
				x += gen[0] + aralik;
				hudKutusu(g, x, y0, gen[1], h, "DALGA", String.valueOf(dalga), CAMGOBEGI);
				x += gen[1] + aralik;
				yemKutusu(g, x, y0, gen[2], h);
				x += gen[2] + aralik;
				oyuncuSkoru(g, x, y0, gen[3], h, b);
			}
		}

		private void hudKutusu(Graphics2D g, double x, double y, double w, double h, String etiket, String deger,
				Color vurgu) {
			neonKutu(g, new RoundRectangle2D.Double(x, y, w, h, 18, 18), vurgu, 170, 0.6);
			g.setFont(kalin(12));
			g.setColor(vurgu);
			g.drawString(etiket, (float) (x + 16), (float) (y + 22));
			if (!deger.isEmpty()) {
				g.setFont(kalin(26));
				g.setColor(Color.WHITE);
				g.drawString(deger, (float) (x + 16), (float) (y + h - 12));
			}
		}

		private void yemKutusu(Graphics2D g, double x, double y, double w, double h) {
			hudKutusu(g, x, y, w, h, "KALAN YEM  " + yemler.size(), "", PEMBE);
			for (int i = 0; i < dalgaBoyu; i++) {
				double cx = x + 24 + i * 19, cy = y + h - 22;
				Shape s = daire(cx, cy, 7);
				if (i < yemler.size()) {
					g.setColor(PEMBE);
					g.fill(s);
				}
				g.setColor(Color.WHITE);
				g.setStroke(new BasicStroke(2f));
				g.draw(s);
			}
		}

		private void seriKutusu(Graphics2D g, double x, double y, double w, double h, Yilan yilan, Color vurgu) {
			boolean sicak = yilan.seri > 1;
			hudKutusu(g, x, y, w, h, "SERİ", "", sicak ? SARI : vurgu);
			g.setFont(kalin(22));
			g.setColor(sicak ? SARI : SOLUK);
			g.drawString("x" + Math.max(yilan.seri, 1), (float) (x + w - 52), (float) (y + 26));
			RoundRectangle2D cubuk = new RoundRectangle2D.Double(x + 16, y + h - 22, w - 32, 10, 10, 10);
			g.setColor(new Color(255, 255, 255, 40));
			g.fill(cubuk);
			double oran = sinirla(yilan.seriKalan / SERI_SURESI, 0, 1);
			g.setColor(sicak ? SARI : PEMBE);
			g.fill(new RoundRectangle2D.Double(x + 16, y + h - 22, (w - 32) * oran, 10, 10, 10));
		}

		private void oyuncuSkoru(Graphics2D g, double x, double y, double w, double h, Yilan yilan) {
			Color renk = yilan.palet.acik();
			neonKutu(g, new RoundRectangle2D.Double(x, y, w, h, 18, 18), renk, 170, yilan.olu ? 0.2 : 0.8);
			g.setColor(yilan.palet.ana());
			g.fill(daire(x + 30, y + h / 2, 16));
			g.setColor(Color.WHITE);
			g.fill(daire(x + 25, y + h / 2 - 3, 5));
			g.fill(daire(x + 35, y + h / 2 - 3, 5));
			g.setColor(MUREKKEP);
			g.fill(daire(x + 26, y + h / 2 - 2, 2.5));
			g.fill(daire(x + 36, y + h / 2 - 2, 2.5));
			g.setFont(kalin(12));
			g.setColor(renk);
			g.drawString(yilan.palet.ad() + (yilan.olu ? "  (ÇARPTI)" : ""), (float) (x + 56), (float) (y + 22));
			g.setFont(kalin(26));
			g.setColor(Color.WHITE);
			g.drawString(String.valueOf(yilan.skor), (float) (x + 56), (float) (y + h - 12));
			if (yilan.seri > 1) {
				g.setFont(kalin(18));
				g.setColor(SARI);
				g.drawString("x" + yilan.seri, (float) (x + w - 44), (float) (y + h - 14));
			}
		}

		/** Damalı tahta her karede yeniden çizilmesin diye bir kez resme çizilip saklanır. */
		private BufferedImage tahtaResmi(int w, int h, double hucre) {
			if (tahtaOnbellek == null || tahtaOnbellek.getWidth() != w || tahtaOnbellek.getHeight() != h
					|| tahtaHucre != hucre) {
				tahtaHucre = hucre;
				tahtaOnbellek = new BufferedImage(Math.max(w, 1), Math.max(h, 1), BufferedImage.TYPE_INT_RGB);
				Graphics2D g = tahtaOnbellek.createGraphics();
				g.scale(hucre, hucre);
				tahtaCiz(g);
				g.dispose();
			}
			return tahtaOnbellek;
		}

		private void tahtaCiz(Graphics2D g) {
			g.setColor(new Color(0xffd23f));
			g.fill(new Rectangle2D.Double(0, 0, sutun, satir));
			g.setColor(new Color(0xffc41f));
			for (int y = 0; y < satir; y++) {
				for (int x = (y % 2 == 0) ? 1 : 0; x < sutun; x += 2) {
					g.fill(new Rectangle2D.Double(x, y, 1, 1));
				}
			}
			g.setColor(new Color(240, 110, 0, 100));
			g.fill(new Rectangle2D.Double(0, 0, sutun, 0.16));
			g.fill(new Rectangle2D.Double(0, 0, 0.16, satir));
		}

		private void yemleriCiz(Graphics2D g) {
			for (Yem yem : yemler) {
				double yas = t - yem.dogus;
				if (yas < 0) {
					continue;
				}
				double buyume = yas < 0.35 ? geriYaylan(yas / 0.35) : 1;
				if (buyume < 0.02) {
					continue;
				}
				double sallanma = Math.sin(t * 3.4 + yem.faz) * 0.05;
				double cx = yem.x + 0.5, cy = yem.y + 0.5;
				g.setColor(new Color(255, 255, 255, 150));
				g.fill(daire(cx, cy, 0.56 * buyume));
				g.setColor(new Color(160, 80, 0, 70));
				g.fill(new Ellipse2D.Double(cx - 0.3 * buyume, cy + 0.35, 0.6 * buyume, 0.14 * buyume));
				double boy = 0.98 * buyume;
				Graphics2D ig = (Graphics2D) g.create();
				ig.translate(cx - boy / 2, cy - boy / 2 - 0.04 + sallanma);
				ig.scale(boy / 100, boy / 100);
				Cizim.kartIkonu(ig, YEM_GORSEL[yem.tur]);
				ig.dispose();
				if (yem.tur == 2) {
					double parla = (Math.sin(t * 5 + yem.faz) + 1) / 2;
					Graphics2D p = (Graphics2D) g.create();
					p.setStroke(new BasicStroke(0.03f));
					Cizim.parilti(p, cx + 0.34, cy - 0.34, 0.08 + parla * 0.13);
					p.dispose();
				}
			}
		}

		// ----- Yılan çizimi (yuvarlak gövde, karın şeridi, baklava desenleri, sevimli kafa) -----
		private double yaricap(Yilan y, double i, int n) {
			double s = i / Math.max(n - 1, 1);
			double incelme = s < 0.55 ? 1 : lerp(1, 0.42, (s - 0.55) / 0.45);
			double r = 0.37 * incelme;
			for (double[] yudum : y.yudumlar) {
				double d = (i - yudum[0]) / 1.1;
				r *= 1 + 0.32 * Math.exp(-d * d);
			}
			return r;
		}

		private void yilanCiz(Graphics2D g, Yilan y) {
			int n = y.govde.size();
			double[] px = new double[n], py = new double[n], nx = new double[n], ny = new double[n];
			for (int i = 0; i < n; i++) {
				Nokta simdi = y.govde.get(i);
				Nokta once = i < y.onceki.size() ? y.onceki.get(i) : simdi;
				px[i] = lerp(once.x(), simdi.x(), alfaDegeri) + 0.5;
				py[i] = lerp(once.y(), simdi.y(), alfaDegeri) + 0.5;
			}
			for (int i = 0; i < n; i++) {
				int a = Math.min(i + 1, n - 1), b = Math.max(i - 1, 0);
				double dx = px[b] - px[a], dy = py[b] - py[a];
				double uzunluk = Math.hypot(dx, dy);
				if (uzunluk == 0) {
					uzunluk = 1;
				}
				nx[i] = -dy / uzunluk;
				ny[i] = dx / uzunluk;
			}
			Palet p = y.palet;
			katman(g, y, px, py, nx, ny, new Color(150, 70, 0, 70), 0.05, 1, 0.07, 0.13, 0);
			katman(g, y, px, py, nx, ny, p.murekkep(), 0.075, 1, 0, 0, 0);
			katman(g, y, px, py, nx, ny, p.ana(), 0, 1, 0, 0, 0);
			katman(g, y, px, py, nx, ny, p.acik(), 0, 0.34, 0, 0, -0.42);
			katman(g, y, px, py, nx, ny, p.karin(), 0, 0.3, 0, 0, 0.62);

			// Görseldeki gibi: dönüşümlü dolu koyu ve içi boş açık baklavalar
			for (int i = n - 2; i >= 1; i--) {
				double r = yaricap(y, i, n);
				if (r < 0.2) {
					continue;
				}
				double aci = Math.atan2(py[i - 1] - py[i + 1], px[i - 1] - px[i + 1]);
				double s = r * 0.62;
				Graphics2D d = (Graphics2D) g.create();
				d.translate(px[i], py[i]);
				d.rotate(aci);
				Path2D baklava = new Path2D.Double();
				baklava.moveTo(s, 0);
				baklava.lineTo(0, s * 0.82);
				baklava.lineTo(-s, 0);
				baklava.lineTo(0, -s * 0.82);
				baklava.closePath();
				if (i % 2 == 1) {
					d.setColor(p.koyu());
					d.fill(baklava);
				} else {
					d.setColor(p.karin());
					d.setStroke(new BasicStroke(0.065f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					d.draw(baklava);
				}
				d.dispose();
			}
			kafaCiz(g, y, px[0], py[0]);
		}

		private void katman(Graphics2D g, Yilan y, double[] px, double[] py, double[] nx, double[] ny, Color renk,
				double ek, double olcek, double ox, double oy, double yan) {
			g.setColor(renk);
			int n = px.length;
			// Kenara kaydırılmış şeritler köşelerde kırılmasın diye her parça 4'e bölünür.
			int bolum = yan == 0 ? 1 : 4;
			for (int i = n - 1; i > 0; i--) {
				for (int k = 0; k < bolum; k++) {
					double a = (double) k / bolum, b = (double) (k + 1) / bolum;
					double ra = yaricap(y, i - a, n), rb = yaricap(y, i - b, n);
					double w = (ra + rb) * olcek + 2 * ek;
					g.setStroke(new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.draw(new Line2D.Double(
							lerp(px[i], px[i - 1], a) + ox + lerp(nx[i], nx[i - 1], a) * ra * yan,
							lerp(py[i], py[i - 1], a) + oy + lerp(ny[i], ny[i - 1], a) * ra * yan,
							lerp(px[i], px[i - 1], b) + ox + lerp(nx[i], nx[i - 1], b) * rb * yan,
							lerp(py[i], py[i - 1], b) + oy + lerp(ny[i], ny[i - 1], b) * rb * yan));
				}
			}
		}

		private void kafaCiz(Graphics2D g, Yilan y, double x, double yy) {
			Palet p = y.palet;
			boolean olu = y.olu;
			Yem enYakin = null;
			double enYakinMesafe = Double.MAX_VALUE;
			for (Yem yem : yemler) {
				double m = Math.hypot(yem.x + 0.5 - x, yem.y + 0.5 - yy);
				if (m < enYakinMesafe) {
					enYakinMesafe = m;
					enYakin = yem;
				}
			}
			boolean agizAcik = !olu && enYakin != null && enYakinMesafe < 2.1;

			// Gölge
			Graphics2D gl = (Graphics2D) g.create();
			gl.translate(x + 0.07, yy + 0.13);
			gl.rotate(y.aci);
			gl.setColor(new Color(150, 70, 0, 70));
			kafaSekli(gl, 0.03);
			gl.dispose();

			Graphics2D k = (Graphics2D) g.create();
			k.translate(x, yy);
			k.rotate(y.aci);

			// Dil: aralıklarla dışarı fırlar
			double fiske = Math.sin(t * 2.3 + y.dilFaz);
			if (!olu && fiske > 0.15) {
				double uzunluk = 0.28 * Math.min((fiske - 0.15) * 3, 1);
				double salla = Math.sin(t * 26) * 0.05;
				for (int katman = 0; katman < 2; katman++) {
					k.setColor(katman == 0 ? p.murekkep() : new Color(0xff3d6e));
					k.setStroke(new BasicStroke(katman == 0 ? 0.13f : 0.07f, BasicStroke.CAP_ROUND,
							BasicStroke.JOIN_ROUND));
					Path2D dil = new Path2D.Double();
					dil.moveTo(0.78, 0);
					dil.lineTo(0.84 + uzunluk, salla);
					dil.lineTo(0.96 + uzunluk, salla - 0.09);
					dil.moveTo(0.84 + uzunluk, salla);
					dil.lineTo(0.96 + uzunluk, salla + 0.09);
					k.draw(dil);
				}
			}

			k.setColor(p.murekkep());
			kafaSekli(k, 0.075);
			k.setColor(p.bas());
			kafaSekli(k, 0);

			// Leke
			k.setColor(p.koyu());
			Path2D leke = new Path2D.Double();
			leke.moveTo(-0.24, 0);
			leke.lineTo(-0.38, 0.14);
			leke.lineTo(-0.52, 0);
			leke.lineTo(-0.38, -0.14);
			leke.closePath();
			k.fill(leke);

			// Açık renk çene
			k.setColor(p.karin());
			k.fill(new Ellipse2D.Double(0.18, -0.36, 0.64, 0.72));

			if (agizAcik) {
				k.setColor(p.murekkep());
				k.fill(new Ellipse2D.Double(0.39, -0.27, 0.34, 0.54));
				k.setColor(new Color(0xff3d6e));
				k.fill(new Ellipse2D.Double(0.51, -0.16, 0.18, 0.32));
			} else {
				k.setColor(p.murekkep());
				k.setStroke(new BasicStroke(0.06f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				k.draw(new Arc2D.Double(0, -0.36, 0.72, 0.72, -60, 120, Arc2D.OPEN));
				k.fill(daire(0.56, -0.12, 0.035));
				k.fill(daire(0.56, 0.12, 0.035));
			}

			// Gözler en yakın yeme bakar
			double bakX = 0.6, bakY = 0;
			if (enYakin != null && !olu) {
				double wx = enYakin.x + 0.5 - x, wy = enYakin.y + 0.5 - yy;
				double l = Math.hypot(wx, wy);
				if (l == 0) {
					l = 1;
				}
				double cos = Math.cos(-y.aci), sin = Math.sin(-y.aci);
				bakX = (wx * cos - wy * sin) / l;
				bakY = (wx * sin + wy * cos) / l;
			}
			boolean kirpiyor = t < y.kirpmaBitis;
			for (int taraf = -1; taraf <= 1; taraf += 2) {
				gozCiz(k, y, 0.03, taraf * 0.38, bakX, bakY, olu, kirpiyor);
			}
			k.dispose();
		}

		private void kafaSekli(Graphics2D g, double buyut) {
			g.fill(new Ellipse2D.Double(-0.04 - 0.56 - buyut, -0.6 - buyut, 2 * (0.56 + buyut),
					2 * (0.6 + buyut)));
			g.fill(new Ellipse2D.Double(0.34 - 0.5 - buyut, -0.45 - buyut, 2 * (0.5 + buyut), 2 * (0.45 + buyut)));
		}

		private void gozCiz(Graphics2D g, Yilan y, double x, double yy, double bakX, double bakY, boolean olu,
				boolean kirpiyor) {
			Palet p = y.palet;
			double r = 0.3;
			g.setColor(p.murekkep());
			g.fill(daire(x, yy, r + 0.06));
			if (olu || kirpiyor) {
				g.setColor(p.bas());
				g.fill(daire(x, yy, r));
				g.setColor(p.murekkep());
				g.setStroke(new BasicStroke(0.075f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				if (olu) {
					double d = r * 0.55;
					g.draw(new Line2D.Double(x - d, yy - d, x + d, yy + d));
					g.draw(new Line2D.Double(x + d, yy - d, x - d, yy + d));
				} else {
					g.draw(new Line2D.Double(x, yy - r * 0.7, x, yy + r * 0.7));
				}
				return;
			}
			g.setColor(Color.WHITE);
			g.fill(daire(x, yy, r));
			double gx = x + bakX * 0.1, gy = yy + bakY * 0.1;
			g.setColor(new Color(0x12140f));
			g.fill(daire(gx, gy, 0.195));
			Graphics2D parlak = (Graphics2D) g.create();
			parlak.translate(gx, gy);
			parlak.rotate(-y.aci);
			parlak.setColor(Color.WHITE);
			parlak.fill(daire(-0.07, -0.08, 0.07));
			parlak.fill(daire(0.07, 0.07, 0.03));
			parlak.dispose();
		}
	}

	// =====================================================================
	// 4) HAFIZA OYUNU (10 x 5 = 50 kart, 25 çift)
	// =====================================================================
	static final class HafizaOyunu extends ArkaPlanPaneli implements Ekran {
		private static final long serialVersionUID = 1L;
		static final int SUTUN = 10, SATIR = 5, KART_SAYISI = SUTUN * SATIR;
		/** Bilgisayarın daha önce gördüğü bir kartı hatırlama ihtimali. */
		static final double HATIRLAMA = 0.5;

		private final MiniOyunMerkezi pencere;
		private final UstBar ustBar;
		private final Tuval tuval = new Tuval();
		private final int[] yuz = new int[KART_SAYISI];
		private final boolean[] eslesti = new boolean[KART_SAYISI];
		private final boolean[] acik = new boolean[KART_SAYISI];
		private final double[] donme = new double[KART_SAYISI];
		private final int[] sahip = new int[KART_SAYISI];
		private final Rectangle2D[] alanlar = new Rectangle2D[KART_SAYISI];
		private final List<Integer> secili = new ArrayList<>();
		private final Map<Integer, Integer> bilgisayarHafizasi = new HashMap<>();
		private final List<Parcacik> parcaciklar = new ArrayList<>();
		private final int[] puan = new int[2];
		private List<DugmeAlani> dugmeler = new ArrayList<>();
		private int kisi = 1, sira, hamle, uzerindeki = -1;
		private boolean bitti;
		private Runnable bekleyen;
		private double bekleKalan, t;
		private Point fare;

		HafizaOyunu(MiniOyunMerkezi pencere) {
			super(new BorderLayout());
			this.pencere = pencere;
			ustBar = new UstBar("HAFIZA KARTLARI", "Merkez", pencere::merkezeGit);
			add(ustBar, BorderLayout.NORTH);
			add(tuval, BorderLayout.CENTER);
			yeniOyun(1);
		}

		void odakla() {
			SwingUtilities.invokeLater(tuval::requestFocusInWindow);
		}

		/** Yeni oyun: kartlar her seferinde karıştırılır, yerleri değişir. */
		void yeniOyun(int kisiSayisi) {
			kisi = kisiSayisi;
			List<Integer> deste = new ArrayList<>();
			for (int i = 0; i < KART_SAYISI / 2; i++) {
				deste.add(i);
				deste.add(i);
			}
			Collections.shuffle(deste, RASTGELE);
			for (int i = 0; i < KART_SAYISI; i++) {
				yuz[i] = deste.get(i);
				eslesti[i] = false;
				acik[i] = false;
				donme[i] = 0;
				sahip[i] = -1;
			}
			secili.clear();
			bilgisayarHafizasi.clear();
			parcaciklar.clear();
			puan[0] = puan[1] = 0;
			sira = 0;
			hamle = 0;
			bitti = false;
			bekleyen = null;
			ustBar.baslikAyarla(kisi == 1 ? "HAFIZA  •  BİLGİSAYARA KARŞI" : "HAFIZA  •  2 KİŞİLİK");
		}

		private String isim(int oyuncu) {
			if (kisi == 1) {
				return oyuncu == 0 ? "SEN" : "BİLGİSAYAR";
			}
			return oyuncu == 0 ? "OYUNCU 1" : "OYUNCU 2";
		}

		private Color renk(int oyuncu) {
			return oyuncu == 0 ? PEMBE : CAMGOBEGI;
		}

		private boolean bilgisayarSirasi() {
			return kisi == 1 && sira == 1;
		}

		private boolean insanOynayabilir() {
			return !bitti && bekleyen == null && !bilgisayarSirasi() && secili.size() < 2;
		}

		private void bekle(double saniye, Runnable is) {
			bekleKalan = saniye;
			bekleyen = is;
		}

		@Override
		public void guncelle(double dt) {
			t += dt;
			for (int i = 0; i < KART_SAYISI; i++) {
				double hedef = (acik[i] || eslesti[i]) ? 1 : 0;
				if (donme[i] < hedef) {
					donme[i] = Math.min(hedef, donme[i] + dt / 0.28);
				} else if (donme[i] > hedef) {
					donme[i] = Math.max(hedef, donme[i] - dt / 0.28);
				}
			}
			if (bekleyen != null) {
				bekleKalan -= dt;
				if (bekleKalan <= 0) {
					Runnable is = bekleyen;
					bekleyen = null;
					is.run();
				}
			}
			parcaciklar.removeIf(p -> !p.guncelle(dt));
			tuval.repaint();
		}

		private void kartAc(int i) {
			acik[i] = true;
			Ses.kart();
			bilgisayarHafizasi.put(i, yuz[i]);
			secili.add(i);
			if (secili.size() == 2) {
				hamle++;
				bekle(0.5, this::kontrolEt);
			}
		}

		private void kontrolEt() {
			int a = secili.get(0), b = secili.get(1);
			if (yuz[a] == yuz[b]) {
				eslesti[a] = eslesti[b] = true;
				sahip[a] = sahip[b] = sira;
				puan[sira]++;
				bilgisayarHafizasi.remove(a);
				bilgisayarHafizasi.remove(b);
				secili.clear();
				Ses.eslesme();
				for (int i : new int[] { a, b }) {
					if (alanlar[i] != null) {
						patlama(parcaciklar, alanlar[i].getCenterX(), alanlar[i].getCenterY(), renk(sira), 18, 260,
								8, 420);
					}
				}
				if (puan[0] + puan[1] == KART_SAYISI / 2) {
					bitir();
				} else if (bilgisayarSirasi()) {
					bekle(0.7, this::bilgisayarOyna); // Eşleşme bulan tekrar oynar.
				}
			} else {
				Ses.hata();
				bekle(0.85, () -> {
					acik[a] = false;
					acik[b] = false;
					secili.clear();
					sira = 1 - sira;
					if (bilgisayarSirasi()) {
						bekle(0.6, this::bilgisayarOyna);
					}
				});
			}
		}

		/** Sanal oyuncu: çoğunlukla rastgele seçer, bazen gördüğü kartları hatırlar. */
		private void bilgisayarOyna() {
			int ilk = ilkKartiSec();
			kartAc(ilk);
			bekle(0.75, () -> kartAc(ikinciKartiSec(ilk)));
		}

		private List<Integer> kapaliKartlar(int haric) {
			List<Integer> liste = new ArrayList<>();
			for (int i = 0; i < KART_SAYISI; i++) {
				if (!eslesti[i] && !acik[i] && i != haric) {
					liste.add(i);
				}
			}
			return liste;
		}

		private int ilkKartiSec() {
			if (RASTGELE.nextDouble() < HATIRLAMA) {
				Map<Integer, Integer> gorulen = new HashMap<>();
				for (Map.Entry<Integer, Integer> e : bilgisayarHafizasi.entrySet()) {
					if (eslesti[e.getKey()] || acik[e.getKey()]) {
						continue;
					}
					Integer onceki = gorulen.put(e.getValue(), e.getKey());
					if (onceki != null) {
						return onceki; // Eşini bildiği bir kart
					}
				}
			}
			List<Integer> kapali = kapaliKartlar(-1);
			return kapali.get(RASTGELE.nextInt(kapali.size()));
		}

		private int ikinciKartiSec(int ilk) {
			if (RASTGELE.nextDouble() < HATIRLAMA) {
				for (Map.Entry<Integer, Integer> e : bilgisayarHafizasi.entrySet()) {
					int k = e.getKey();
					if (k != ilk && e.getValue() == yuz[ilk] && !eslesti[k] && !acik[k]) {
						return k;
					}
				}
			}
			List<Integer> kapali = kapaliKartlar(ilk);
			return kapali.get(RASTGELE.nextInt(kapali.size()));
		}

		private void bitir() {
			bitti = true;
			Ses.zafer();
			konfeti(parcaciklar, tuval.getWidth(), 380, 11, 220);
		}

		private int kartBul(Point p) {
			for (int i = 0; i < KART_SAYISI; i++) {
				if (alanlar[i] != null && alanlar[i].contains(p)) {
					return i;
				}
			}
			return -1;
		}

		final class Tuval extends JComponent {
			private static final long serialVersionUID = 1L;

			Tuval() {
				setOpaque(false);
				setFocusable(true);
				addKeyListener(new KeyAdapter() {
					@Override
					public void keyPressed(KeyEvent e) {
						if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
							pencere.merkezeGit();
						} else if (bitti && (e.getKeyCode() == KeyEvent.VK_ENTER
								|| e.getKeyCode() == KeyEvent.VK_SPACE)) {
							yeniOyun(kisi);
						}
					}
				});
				MouseAdapter fareDinleyici = new MouseAdapter() {
					@Override
					public void mousePressed(MouseEvent e) {
						requestFocusInWindow();
						for (DugmeAlani d : dugmeler) {
							if (d.alan().contains(e.getPoint())) {
								Ses.tik();
								d.is().run();
								return;
							}
						}
						if (!insanOynayabilir()) {
							return;
						}
						int i = kartBul(e.getPoint());
						if (i >= 0 && !eslesti[i] && !acik[i]) {
							kartAc(i);
						}
					}

					@Override
					public void mouseMoved(MouseEvent e) {
						fare = e.getPoint();
						uzerindeki = kartBul(fare);
						boolean tiklanabilir = uzerindeki >= 0 && insanOynayabilir() && !eslesti[uzerindeki]
								&& !acik[uzerindeki];
						setCursor(Cursor.getPredefinedCursor(
								tiklanabilir ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
					}
				};
				addMouseListener(fareDinleyici);
				addMouseMotionListener(fareDinleyici);
			}

			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = hazirla(g0);
				ciz(g, getWidth(), getHeight());
				g.dispose();
			}
		}

		private void ciz(Graphics2D g, int W, int H) {
			// Üst bilgi: iki oyuncu ve ortada kalan çift
			double hudY = 4, hudH = 76, panelW = Math.min(320, W * 0.27);
			oyuncuPaneli(g, 24, hudY, panelW, hudH, 0);
			oyuncuPaneli(g, W - 24 - panelW, hudY, panelW, hudH, 1);
			int kalan = KART_SAYISI / 2 - puan[0] - puan[1];
			ortaYazi(g, "KALAN ÇİFT", kalin(13), W / 2.0, hudY + 24, SOLUK);
			isikliYazi(g, String.valueOf(kalan), kalin(34), W / 2.0, hudY + 62, Color.WHITE, MOR, 0.8);
			ortaYazi(g, "Hamle: " + hamle, yazi(Font.BOLD, 13), W / 2.0, hudY + 82, alfa(SOLUK, 200));

			// Kartlar
			double ust = hudY + hudH + 22, alt = 16, aralik = 10;
			double kw = (W - 48 - (SUTUN - 1) * aralik) / SUTUN;
			double kh = kw / 0.8;
			double maksH = (H - ust - alt - (SATIR - 1) * aralik) / SATIR;
			if (kh > maksH) {
				kh = maksH;
				kw = kh * 0.8;
			}
			double toplamW = SUTUN * kw + (SUTUN - 1) * aralik, toplamH = SATIR * kh + (SATIR - 1) * aralik;
			double ox = (W - toplamW) / 2, oy = ust + (H - ust - alt - toplamH) / 2;
			for (int i = 0; i < KART_SAYISI; i++) {
				double x = ox + (i % SUTUN) * (kw + aralik), y = oy + (i / SUTUN) * (kh + aralik);
				alanlar[i] = new Rectangle2D.Double(x, y, kw, kh);
				kartCiz(g, i, x, y, kw, kh);
			}
			for (Parcacik p : parcaciklar) {
				p.ciz(g);
			}

			dugmeler = new ArrayList<>();
			if (bitti) {
				String baslik;
				Color renk;
				if (puan[0] == puan[1]) {
					baslik = "BERABERE!";
					renk = SARI;
				} else {
					int kazanan = puan[0] > puan[1] ? 0 : 1;
					baslik = kisi == 1 ? (kazanan == 0 ? "KAZANDIN!" : "BİLGİSAYAR KAZANDI") : isim(kazanan) + " KAZANDI!";
					renk = kisi == 1 && kazanan == 0 ? LIME : renk(kazanan);
				}
				dugmeler = bilgiPaneli(g, W, H, baslik, renk,
						new String[] { isim(0) + "  " + puan[0] + "   -   " + puan[1] + "  " + isim(1),
								"Toplam hamle: " + hamle + "   •   Yeni oyunda kartlar yeniden karışır" },
						new String[] { "YENİ OYUN", "MERKEZ" },
						new Runnable[] { () -> yeniOyun(kisi), pencere::merkezeGit }, fare, null);
			}
		}

		private void oyuncuPaneli(Graphics2D g, double x, double y, double w, double h, int oyuncu) {
			boolean aktif = !bitti && sira == oyuncu;
			Color renk = renk(oyuncu);
			double nabiz = aktif ? 0.9 + 0.4 * Math.sin(t * 4) : 0.25;
			RoundRectangle2D r = new RoundRectangle2D.Double(x, y, w, h, 22, 22);
			neonKutu(g, r, aktif ? renk : alfa(renk, 120), 200, nabiz);
			if (aktif) {
				g.setPaint(new GradientPaint(0, (float) y, alfa(renk, 60), 0, (float) (y + h), alfa(renk, 0)));
				g.fill(r);
			}
			double cx = x + 40, cy = y + h / 2;
			g.setColor(alfa(renk, 50));
			g.fill(daire(cx, cy, 26));
			g.setColor(renk);
			g.setStroke(new BasicStroke(2f));
			g.draw(daire(cx, cy, 26));
			if (kisi == 1 && oyuncu == 1) {
				Cizim.robot(g, cx, cy + 4, 0.9, CAMGOBEGI);
			} else {
				Cizim.insan(g, cx, cy + 4, 0.95, Color.WHITE);
			}
			g.setFont(kalin(15));
			g.setColor(renk);
			g.drawString(isim(oyuncu), (float) (x + 80), (float) (y + 28));
			g.setFont(kalin(30));
			g.setColor(Color.WHITE);
			g.drawString(String.valueOf(puan[oyuncu]), (float) (x + 80), (float) (y + 62));
			g.setFont(yazi(Font.BOLD, 13));
			g.setColor(SOLUK);
			g.drawString("çift", (float) (x + 82 + g.getFontMetrics(kalin(30)).stringWidth(
					String.valueOf(puan[oyuncu]))), (float) (y + 61));
			if (aktif) {
				String s = (kisi == 1 && oyuncu == 1) ? "DÜŞÜNÜYOR..." : "SIRA SENDE";
				Font f = kalin(11);
				double sw = g.getFontMetrics(f).stringWidth(s) + 18;
				RoundRectangle2D rozet = new RoundRectangle2D.Double(x + w - sw - 12, y + 12, sw, 22, 22, 22);
				g.setColor(renk);
				g.fill(rozet);
				g.setFont(f);
				g.setColor(MUREKKEP);
				g.drawString(s, (float) (rozet.getX() + 9), (float) (y + 27));
			}
		}

		private void kartCiz(Graphics2D g, int i, double x, double y, double w, double h) {
			double p = donme[i];
			double olcekX = Math.max(Math.abs(Math.cos(Math.PI * p)), 0.02);
			boolean onYuz = p >= 0.5;
			boolean ustunde = i == uzerindeki && insanOynayabilir() && !eslesti[i] && !acik[i];
			double kalkma = ustunde ? -4 : 0;

			Graphics2D c = (Graphics2D) g.create();
			c.translate(x + w / 2, y + h / 2 + kalkma);
			c.scale(olcekX, 1);
			RoundRectangle2D r = new RoundRectangle2D.Double(-w / 2, -h / 2, w, h, 16, 16);
			c.setColor(new Color(0, 0, 0, 90));
			c.fill(new RoundRectangle2D.Double(-w / 2 + 3, -h / 2 + 5, w, h, 16, 16));

			if (!onYuz) {
				c.setPaint(new GradientPaint((float) (-w / 2), (float) (-h / 2), new Color(0x4a26c9),
						(float) (w / 2), (float) (h / 2), new Color(0x1a0f58)));
				c.fill(r);
				Graphics2D desen = (Graphics2D) c.create();
				desen.clip(r);
				desen.setColor(new Color(255, 255, 255, 22));
				desen.setStroke(new BasicStroke(1.2f));
				for (double d = -h; d < h + w; d += 12) {
					desen.draw(new Line2D.Double(-w / 2 + d, -h / 2, -w / 2 + d - h, h / 2));
					desen.draw(new Line2D.Double(-w / 2 + d - h, -h / 2, -w / 2 + d, h / 2));
				}
				desen.dispose();
				c.setColor(alfa(Color.WHITE, 50));
				c.setStroke(new BasicStroke(1.5f));
				c.draw(new RoundRectangle2D.Double(-w / 2 + 5, -h / 2 + 5, w - 10, h - 10, 12, 12));
				Font f = kalin((float) (h * 0.42));
				TextLayout tl = new TextLayout("?", f, c.getFontRenderContext());
				isikliYazi(c, "?", f, 0, tl.getBounds().getHeight() / 2, Color.WHITE, PEMBE, ustunde ? 1.2 : 0.8);
				c.setColor(ustunde ? CAMGOBEGI : alfa(MOR, 220));
				c.setStroke(new BasicStroke(ustunde ? 3f : 2f));
				c.draw(r);
			} else {
				Color tint = Cizim.KART_RENKLERI[yuz[i]];
				c.setPaint(new GradientPaint(0, (float) (-h / 2), tint, 0, (float) (h / 2), Color.WHITE));
				c.fill(r);
				double boy = Math.min(w, h) * 0.8;
				Graphics2D ic = (Graphics2D) c.create();
				ic.translate(-boy / 2, -boy / 2 - h * 0.03);
				ic.scale(boy / 100, boy / 100);
				Cizim.kartIkonu(ic, yuz[i]);
				ic.dispose();
				if (eslesti[i]) {
					Color sahipRenk = renk(sahip[i]);
					double parla = 0.7 + 0.3 * Math.sin(t * 3 + i);
					for (int k = 3; k >= 1; k--) {
						c.setColor(alfa(sahipRenk, (int) (60 * parla / k)));
						c.setStroke(new BasicStroke(k * 4f));
						c.draw(r);
					}
					c.setColor(sahipRenk);
					c.setStroke(new BasicStroke(3f));
				} else {
					c.setColor(MUREKKEP);
					c.setStroke(new BasicStroke(2.5f));
				}
				c.draw(r);
			}
			c.dispose();
		}
	}

	// =====================================================================
	// GÖRSELLER: Bütün resimler kodla çizilir (100 x 100'lük alanda)
	// =====================================================================
	static final class Cizim {
		static final String[] KART_ADLARI = { "Elma", "Kiraz", "Çilek", "Karpuz", "Muz", "Üzüm", "Limon",
				"Dondurma", "Kek", "Yıldız", "Kalp", "Ay", "Güneş", "Bulut", "Şimşek", "Çiçek", "Ağaç", "Mantar",
				"Balık", "Roket", "Elmas", "Taç", "Balon", "Hayalet", "Nota" };
		static final Color[] KART_RENKLERI = { new Color(0xffd6dc), new Color(0xffd0e0), new Color(0xffe0e6),
				new Color(0xd4f7d0), new Color(0xfff3b8), new Color(0xe8d6ff), new Color(0xfffbc2),
				new Color(0xffe0f0), new Color(0xd6f6fa), new Color(0xfff0b3), new Color(0xffd6e4),
				new Color(0xd9dcff), new Color(0xffe6b3), new Color(0xd2ecff), new Color(0xe3dcff),
				new Color(0xffe0f4), new Color(0xd7f5d9), new Color(0xffe1d6), new Color(0xd1f1ff),
				new Color(0xdde3ff), new Color(0xcff9ff), new Color(0xfff1c7), new Color(0xffdbe8),
				new Color(0xece4ff), new Color(0xeadcff) };

		static Color c(int rgb) {
			return new Color(rgb);
		}

		static Shape dondur(Shape s, double aci, double cx, double cy) {
			return AffineTransform.getRotateInstance(aci, cx, cy).createTransformedShape(s);
		}

		static Shape yildiz(double cx, double cy, double dis, double ic, int kose) {
			Path2D p = new Path2D.Double();
			for (int i = 0; i < kose * 2; i++) {
				double a = -Math.PI / 2 + i * Math.PI / kose;
				double r = i % 2 == 0 ? dis : ic;
				if (i == 0) {
					p.moveTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
				} else {
					p.lineTo(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
				}
			}
			p.closePath();
			return p;
		}

		static void parilti(Graphics2D g, double x, double y, double r) {
			Path2D p = new Path2D.Double();
			for (int i = 0; i < 8; i++) {
				double a = i * Math.PI / 4;
				double d = i % 2 == 1 ? r * 0.3 : r;
				if (i == 0) {
					p.moveTo(x + Math.cos(a) * d, y + Math.sin(a) * d);
				} else {
					p.lineTo(x + Math.cos(a) * d, y + Math.sin(a) * d);
				}
			}
			p.closePath();
			g.setColor(Color.WHITE);
			g.fill(p);
		}

		static void cizgiKalinligi(Graphics2D g, double k) {
			g.setStroke(new BasicStroke((float) k, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		}

		/** Hafıza kartlarındaki 25 farklı resim. */
		static void kartIkonu(Graphics2D g0, int no) {
			Graphics2D g = (Graphics2D) g0.create();
			cizgiKalinligi(g, 3.5);
			switch (no) {
			case 0 -> { // Elma
				g.setColor(c(0x7a4a1c));
				cizgiKalinligi(g, 5);
				g.draw(new Line2D.Double(50, 34, 54, 14));
				cizgiKalinligi(g, 3.5);
				Path2D yaprak = new Path2D.Double();
				yaprak.moveTo(54, 22);
				yaprak.quadTo(66, 6, 84, 12);
				yaprak.quadTo(72, 30, 54, 22);
				boya(g, yaprak, c(0x3ccf3c));
				Path2D p = new Path2D.Double();
				p.moveTo(50, 34);
				p.curveTo(63, 20, 92, 24, 89, 52);
				p.curveTo(87, 76, 68, 92, 55, 88);
				p.quadTo(50, 86, 45, 88);
				p.curveTo(32, 92, 13, 76, 11, 52);
				p.curveTo(8, 24, 37, 20, 50, 34);
				boya(g, p, c(0xff2d3f));
				g.setColor(new Color(255, 255, 255, 200));
				g.fill(dondur(new Ellipse2D.Double(24, 38, 10, 18), 0.4, 29, 47));
			}
			case 1 -> { // Kiraz
				g.setColor(c(0x2f8f2f));
				cizgiKalinligi(g, 4);
				g.draw(new QuadCurve2D.Double(34, 66, 40, 30, 58, 14));
				g.draw(new QuadCurve2D.Double(66, 70, 64, 36, 58, 14));
				cizgiKalinligi(g, 3.5);
				Path2D yaprak = new Path2D.Double();
				yaprak.moveTo(58, 14);
				yaprak.quadTo(76, 2, 90, 16);
				yaprak.quadTo(74, 28, 58, 14);
				boya(g, yaprak, c(0x3ccf3c));
				boya(g, daire(34, 70, 17), c(0xe8173a));
				boya(g, daire(66, 74, 17), c(0xff2d55));
				g.setColor(new Color(255, 255, 255, 200));
				g.fill(daire(28, 64, 4.5));
				g.fill(daire(60, 68, 4.5));
			}
			case 2 -> { // Çilek
				Path2D p = new Path2D.Double();
				p.moveTo(50, 92);
				p.curveTo(20, 74, 12, 40, 28, 32);
				p.curveTo(38, 26, 62, 26, 72, 32);
				p.curveTo(88, 40, 80, 74, 50, 92);
				boya(g, p, c(0xff3355));
				g.setColor(c(0xffe066));
				double[][] cekirdek = { { 36, 44 }, { 50, 40 }, { 64, 44 }, { 42, 56 }, { 58, 56 }, { 50, 70 },
						{ 34, 60 }, { 66, 60 } };
				for (double[] k : cekirdek) {
					g.fill(new Ellipse2D.Double(k[0] - 2, k[1] - 3, 4, 6));
				}
				Path2D yaprak = new Path2D.Double();
				yaprak.moveTo(28, 30);
				yaprak.lineTo(38, 20);
				yaprak.lineTo(43, 28);
				yaprak.lineTo(50, 12);
				yaprak.lineTo(57, 28);
				yaprak.lineTo(62, 20);
				yaprak.lineTo(72, 30);
				yaprak.lineTo(50, 38);
				yaprak.closePath();
				boya(g, yaprak, c(0x38c24a));
			}
			case 3 -> { // Karpuz dilimi
				g.translate(0, 14);
				Shape dis = new Arc2D.Double(8, -2, 84, 84, 180, 180, Arc2D.CHORD);
				g.setColor(c(0x2f9e44));
				g.fill(dis);
				g.setColor(c(0xb8f5a0));
				g.fill(new Arc2D.Double(13, 3, 74, 74, 180, 180, Arc2D.CHORD));
				g.setColor(c(0xff4d6d));
				g.fill(new Arc2D.Double(18, 8, 64, 64, 180, 180, Arc2D.CHORD));
				g.setColor(MUREKKEP);
				double[][] cekirdek = { { 36, 50 }, { 50, 58 }, { 64, 50 }, { 43, 64 }, { 57, 64 } };
				for (double[] k : cekirdek) {
					g.fill(new Ellipse2D.Double(k[0] - 2.5, k[1] - 4, 5, 8));
				}
				g.draw(dis);
			}
			case 4 -> { // Muz
				Path2D p = new Path2D.Double();
				p.moveTo(16, 34);
				p.curveTo(24, 86, 78, 92, 90, 54);
				p.curveTo(72, 74, 36, 70, 28, 30);
				p.closePath();
				boya(g, p, c(0xffd93b));
				g.setColor(c(0xe0a800));
				cizgiKalinligi(g, 2);
				g.draw(new CubicCurve2D.Double(24, 44, 36, 72, 66, 80, 82, 62));
				g.setColor(c(0x6b4a1f));
				g.fill(daire(18, 32, 4));
				g.fill(daire(89, 55, 3.5));
			}
			case 5 -> { // Üzüm
				Path2D yaprak = new Path2D.Double();
				yaprak.moveTo(50, 24);
				yaprak.quadTo(62, 6, 82, 12);
				yaprak.quadTo(72, 30, 50, 24);
				boya(g, yaprak, c(0x39c414));
				double[][] taneler = { { 29, 38 }, { 50, 36 }, { 71, 38 }, { 39, 57 }, { 61, 57 }, { 50, 76 } };
				for (double[] k : taneler) {
					boya(g, daire(k[0], k[1], 14.5), c(0x8a2be2));
					g.setColor(new Color(255, 255, 255, 190));
					g.fill(daire(k[0] - 4.5, k[1] - 5, 3.5));
				}
			}
			case 6 -> { // Limon
				Path2D yaprak = new Path2D.Double();
				yaprak.moveTo(62, 26);
				yaprak.quadTo(74, 4, 92, 12);
				yaprak.quadTo(82, 32, 62, 26);
				boya(g, yaprak, c(0x3ccf3c));
				Shape govde = dondur(new Ellipse2D.Double(12, 30, 76, 48), -0.35, 50, 54);
				boya(g, govde, c(0xffe14d));
				boya(g, dondur(new Ellipse2D.Double(8, 50, 10, 8), -0.35, 50, 54), c(0xffd21f));
				g.setColor(new Color(255, 255, 255, 190));
				g.fill(dondur(new Ellipse2D.Double(28, 42, 22, 9), -0.35, 50, 54));
			}
			case 7 -> { // Dondurma
				Path2D kulah = new Path2D.Double();
				kulah.moveTo(30, 50);
				kulah.lineTo(70, 50);
				kulah.lineTo(50, 95);
				kulah.closePath();
				g.setColor(c(0xe8a85c));
				g.fill(kulah);
				Graphics2D h = (Graphics2D) g.create();
				h.clip(kulah);
				h.setColor(c(0xb97a35));
				cizgiKalinligi(h, 2);
				for (int i = -2; i < 6; i++) {
					h.draw(new Line2D.Double(24 + i * 10, 50, 44 + i * 10, 95));
					h.draw(new Line2D.Double(76 - i * 10, 50, 56 - i * 10, 95));
				}
				h.dispose();
				g.setColor(MUREKKEP);
				g.draw(kulah);
				boya(g, daire(50, 46, 19), c(0xff8fb8));
				boya(g, daire(50, 26, 15), c(0x8ef0c8));
				boya(g, daire(50, 9, 6), c(0xe8173a));
			}
			case 8 -> { // Kek
				Path2D kagit = new Path2D.Double();
				kagit.moveTo(24, 56);
				kagit.lineTo(76, 56);
				kagit.lineTo(68, 92);
				kagit.lineTo(32, 92);
				kagit.closePath();
				boya(g, kagit, c(0x39c6d6));
				g.setColor(c(0x1f9fb0));
				cizgiKalinligi(g, 2.5);
				for (int i = 0; i < 5; i++) {
					double x = 34 + i * 8;
					g.draw(new Line2D.Double(x - 2 + i * 0.6, 60, x + (i - 2) * 0.8, 88));
				}
				cizgiKalinligi(g, 3.5);
				Area krema = new Area(daire(34, 52, 14));
				krema.add(new Area(daire(50, 42, 18)));
				krema.add(new Area(daire(66, 52, 14)));
				krema.add(new Area(new RoundRectangle2D.Double(20, 50, 60, 12, 10, 10)));
				boya(g, krema, c(0xff9ad5));
				Color[] seker = { c(0xffd60a), c(0x4dabf7), c(0x2fbf5b), Color.WHITE };
				double[][] yer = { { 36, 48 }, { 46, 36 }, { 58, 44 }, { 66, 52 }, { 44, 54 }, { 54, 30 } };
				for (int i = 0; i < yer.length; i++) {
					g.setColor(seker[i % seker.length]);
					g.fill(dondur(new RoundRectangle2D.Double(yer[i][0] - 4, yer[i][1] - 1.5, 8, 3, 3, 3), i, yer[i][0],
							yer[i][1]));
				}
				boya(g, daire(50, 22, 7), c(0xe8173a));
			}
			case 9 -> { // Yıldız
				boya(g, yildiz(50, 54, 44, 19, 5), c(0xffd60a));
				g.setColor(MUREKKEP);
				g.fill(new Ellipse2D.Double(40, 48, 6, 9));
				g.fill(new Ellipse2D.Double(54, 48, 6, 9));
				cizgiKalinligi(g, 3);
				g.draw(new Arc2D.Double(43, 56, 14, 10, 200, 140, Arc2D.OPEN));
				g.setColor(new Color(255, 120, 150, 160));
				g.fill(daire(36, 62, 4));
				g.fill(daire(64, 62, 4));
			}
			case 10 -> { // Kalp
				Path2D p = new Path2D.Double();
				p.moveTo(50, 88);
				p.curveTo(14, 64, 6, 38, 22, 24);
				p.curveTo(34, 14, 48, 20, 50, 32);
				p.curveTo(52, 20, 66, 14, 78, 24);
				p.curveTo(94, 38, 86, 64, 50, 88);
				p.closePath();
				boya(g, p, c(0xff2e63));
				g.setColor(new Color(255, 255, 255, 200));
				g.fill(dondur(new Ellipse2D.Double(22, 30, 10, 16), -0.6, 27, 38));
			}
			case 11 -> { // Ay
				Area ay = new Area(daire(46, 52, 36));
				ay.subtract(new Area(daire(66, 40, 30)));
				boya(g, ay, c(0xffe59a));
				g.setColor(c(0xffc94d));
				g.fill(yildiz(76, 68, 8, 3.5, 4));
				g.fill(yildiz(84, 30, 6, 2.6, 4));
				g.setColor(new Color(230, 190, 90));
				g.fill(daire(30, 60, 4));
				g.fill(daire(36, 42, 3));
			}
			case 12 -> { // Güneş
				for (int i = 0; i < 12; i++) {
					double a = i * Math.PI / 6;
					Path2D isin = new Path2D.Double();
					isin.moveTo(50 + Math.cos(a - 0.18) * 30, 50 + Math.sin(a - 0.18) * 30);
					isin.lineTo(50 + Math.cos(a) * 47, 50 + Math.sin(a) * 47);
					isin.lineTo(50 + Math.cos(a + 0.18) * 30, 50 + Math.sin(a + 0.18) * 30);
					isin.closePath();
					boya(g, isin, c(0xffa400));
				}
				boya(g, daire(50, 50, 27), c(0xffd60a));
				g.setColor(MUREKKEP);
				g.fill(daire(41, 46, 3.5));
				g.fill(daire(59, 46, 3.5));
				cizgiKalinligi(g, 3);
				g.draw(new Arc2D.Double(40, 48, 20, 14, 200, 140, Arc2D.OPEN));
			}
			case 13 -> { // Bulut ve yağmur
				g.setColor(c(0x4dabf7));
				for (double[] d : new double[][] { { 34, 80 }, { 52, 88 }, { 70, 80 } }) {
					Path2D damla = new Path2D.Double();
					damla.moveTo(d[0], d[1] - 9);
					damla.quadTo(d[0] + 6, d[1], d[0], d[1] + 4);
					damla.quadTo(d[0] - 6, d[1], d[0], d[1] - 9);
					boya(g, damla, c(0x4dabf7));
				}
				Area bulut = new Area(daire(32, 52, 17));
				bulut.add(new Area(daire(52, 40, 22)));
				bulut.add(new Area(daire(71, 52, 16)));
				bulut.add(new Area(new RoundRectangle2D.Double(18, 50, 66, 18, 18, 18)));
				boya(g, bulut, Color.WHITE);
				g.setColor(c(0xd6eaff));
				g.fill(new RoundRectangle2D.Double(24, 58, 54, 7, 7, 7));
			}
			case 14 -> { // Şimşek
				Path2D p = new Path2D.Double();
				p.moveTo(60, 6);
				p.lineTo(22, 56);
				p.lineTo(46, 56);
				p.lineTo(36, 94);
				p.lineTo(80, 40);
				p.lineTo(55, 40);
				p.lineTo(68, 6);
				p.closePath();
				boya(g, p, c(0xffd60a));
				g.setColor(new Color(255, 255, 255, 170));
				g.fill(new Polygon(new int[] { 56, 34, 44 }, new int[] { 14, 50, 50 }, 3));
			}
			case 15 -> { // Çiçek
				g.setColor(c(0x2f9e44));
				cizgiKalinligi(g, 6);
				g.draw(new Line2D.Double(50, 58, 50, 95));
				cizgiKalinligi(g, 3.5);
				boya(g, dondur(new Ellipse2D.Double(52, 72, 24, 12), -0.5, 64, 78), c(0x3ccf3c));
				for (int i = 0; i < 6; i++) {
					boya(g, dondur(new Ellipse2D.Double(41, 10, 18, 30), i * Math.PI / 3, 50, 40), c(0xff6fae));
				}
				boya(g, daire(50, 40, 11), c(0xffd60a));
			}
			case 16 -> { // Ağaç
				boya(g, new RoundRectangle2D.Double(43, 58, 14, 34, 4, 4), c(0x8b5a2b));
				Area yaprak = new Area(daire(32, 48, 19));
				yaprak.add(new Area(daire(50, 32, 22)));
				yaprak.add(new Area(daire(68, 48, 19)));
				yaprak.add(new Area(daire(50, 54, 18)));
				boya(g, yaprak, c(0x2fbf5b));
				for (double[] e : new double[][] { { 38, 40 }, { 62, 50 }, { 54, 26 }, { 44, 60 } }) {
					boya(g, daire(e[0], e[1], 4.5), c(0xff3b3b));
				}
			}
			case 17 -> { // Mantar
				boya(g, new RoundRectangle2D.Double(38, 50, 24, 40, 12, 12), c(0xfff1d6));
				boya(g, new Arc2D.Double(12, 14, 76, 72, 0, 180, Arc2D.CHORD), c(0xff3b3b));
				g.setColor(Color.WHITE);
				g.fill(daire(32, 38, 7));
				g.fill(daire(54, 28, 8));
				g.fill(daire(72, 42, 5.5));
				g.fill(daire(46, 44, 4));
			}
			case 18 -> { // Balık
				Path2D kuyruk = new Path2D.Double();
				kuyruk.moveTo(26, 50);
				kuyruk.lineTo(6, 32);
				kuyruk.lineTo(6, 68);
				kuyruk.closePath();
				boya(g, kuyruk, c(0xff9f1c));
				boya(g, new Ellipse2D.Double(18, 30, 64, 40), c(0xffb703));
				boya(g, new Arc2D.Double(38, 22, 24, 22, 0, 180, Arc2D.CHORD), c(0xff9f1c));
				g.setColor(Color.WHITE);
				g.fill(daire(66, 46, 7));
				g.setColor(MUREKKEP);
				g.fill(daire(68, 46, 3.5));
				g.setColor(c(0x4dabf7));
				cizgiKalinligi(g, 2.5);
				g.draw(daire(88, 30, 4));
				g.draw(daire(92, 16, 3));
			}
			case 19 -> { // Roket
				Path2D alev = new Path2D.Double();
				alev.moveTo(40, 74);
				alev.lineTo(60, 74);
				alev.lineTo(50, 98);
				alev.closePath();
				boya(g, alev, c(0xff7b00));
				g.setColor(c(0xffd60a));
				g.fill(new Polygon(new int[] { 45, 55, 50 }, new int[] { 76, 76, 90 }, 3));
				boya(g, new Polygon(new int[] { 38, 24, 38 }, new int[] { 54, 76, 72 }, 3), c(0xff3b5c));
				boya(g, new Polygon(new int[] { 62, 76, 62 }, new int[] { 54, 76, 72 }, 3), c(0xff3b5c));
				Path2D govde = new Path2D.Double();
				govde.moveTo(50, 6);
				govde.curveTo(70, 22, 68, 58, 62, 76);
				govde.lineTo(38, 76);
				govde.curveTo(32, 58, 30, 22, 50, 6);
				govde.closePath();
				boya(g, govde, c(0xf1f3ff));
				boya(g, daire(50, 40, 9), c(0x4dabf7));
				g.setColor(new Color(255, 255, 255, 200));
				g.fill(daire(47, 37, 3));
			}
			case 20 -> { // Elmas
				Path2D dis = new Path2D.Double();
				dis.moveTo(24, 20);
				dis.lineTo(76, 20);
				dis.lineTo(93, 43);
				dis.lineTo(50, 92);
				dis.lineTo(7, 43);
				dis.closePath();
				g.setColor(c(0x18d6f0));
				g.fill(dis);
				g.setColor(c(0x8ff0ff));
				g.fill(new Polygon(new int[] { 36, 64, 50 }, new int[] { 43, 43, 92 }, 3));
				g.setColor(c(0xd8fbff));
				g.fill(new Polygon(new int[] { 24, 48, 36, 7 }, new int[] { 20, 20, 43, 43 }, 4));
				g.setColor(MUREKKEP);
				g.draw(dis);
				cizgiKalinligi(g, 2);
				g.draw(new Line2D.Double(7, 43, 93, 43));
			}
			case 21 -> { // Taç
				Path2D tac = new Path2D.Double();
				tac.moveTo(14, 74);
				tac.lineTo(14, 32);
				tac.lineTo(32, 52);
				tac.lineTo(50, 24);
				tac.lineTo(68, 52);
				tac.lineTo(86, 32);
				tac.lineTo(86, 74);
				tac.closePath();
				boya(g, tac, c(0xffc300));
				boya(g, new RoundRectangle2D.Double(12, 72, 76, 14, 6, 6), c(0xffa000));
				boya(g, daire(14, 28, 5), c(0xffd60a));
				boya(g, daire(50, 20, 5), c(0xffd60a));
				boya(g, daire(86, 28, 5), c(0xffd60a));
				boya(g, daire(50, 58, 6), c(0xff2d55));
				boya(g, daire(30, 62, 4.5), c(0x4dabf7));
				boya(g, daire(70, 62, 4.5), c(0x2fbf5b));
			}
			case 22 -> { // Balon
				g.setColor(c(0x7a7a9a));
				cizgiKalinligi(g, 2.5);
				g.draw(new CubicCurve2D.Double(50, 74, 40, 84, 60, 90, 48, 99));
				cizgiKalinligi(g, 3.5);
				boya(g, new Polygon(new int[] { 46, 54, 50 }, new int[] { 78, 78, 70 }, 3), c(0xe6336f));
				boya(g, new Ellipse2D.Double(22, 6, 56, 66), c(0xff4d8d));
				g.setColor(new Color(255, 255, 255, 200));
				g.fill(dondur(new Ellipse2D.Double(32, 18, 10, 20), 0.4, 37, 28));
			}
			case 23 -> { // Hayalet
				Path2D p = new Path2D.Double();
				p.moveTo(22, 90);
				p.lineTo(22, 44);
				p.curveTo(22, 20, 40, 10, 50, 10);
				p.curveTo(60, 10, 78, 20, 78, 44);
				p.lineTo(78, 90);
				p.quadTo(71, 80, 64, 90);
				p.quadTo(57, 80, 50, 90);
				p.quadTo(43, 80, 36, 90);
				p.quadTo(29, 80, 22, 90);
				p.closePath();
				boya(g, p, c(0xf3eaff));
				g.setColor(MUREKKEP);
				g.fill(new Ellipse2D.Double(35, 36, 9, 13));
				g.fill(new Ellipse2D.Double(56, 36, 9, 13));
				g.fill(new Ellipse2D.Double(45, 54, 10, 9));
				g.setColor(new Color(255, 120, 170, 150));
				g.fill(daire(33, 54, 5));
				g.fill(daire(67, 54, 5));
			}
			default -> { // Müzik notası
				g.setColor(MUREKKEP);
				cizgiKalinligi(g, 5);
				g.draw(new Line2D.Double(44, 74, 44, 26));
				g.draw(new Line2D.Double(80, 66, 80, 18));
				g.fill(new Polygon(new int[] { 42, 82, 82, 42 }, new int[] { 24, 16, 30, 38 }, 4));
				cizgiKalinligi(g, 3.5);
				boya(g, dondur(new Ellipse2D.Double(22, 66, 24, 17), -0.4, 34, 74), c(0x9b5de5));
				boya(g, dondur(new Ellipse2D.Double(58, 58, 24, 17), -0.4, 70, 66), c(0x9b5de5));
			}
			}
			g.dispose();
		}

		/** Logodaki küçük hafıza kartı (ön yüz). */
		static void kucukKart(Graphics2D g, int no, double w, double h) {
			RoundRectangle2D r = new RoundRectangle2D.Double(-w / 2, -h / 2, w, h, 14, 14);
			for (int i = 3; i >= 1; i--) {
				g.setColor(alfa(PEMBE, 40 / i));
				g.setStroke(new BasicStroke(i * 4f));
				g.draw(r);
			}
			g.setPaint(new GradientPaint(0, (float) (-h / 2), KART_RENKLERI[no], 0, (float) (h / 2), Color.WHITE));
			g.fill(r);
			g.setColor(MUREKKEP);
			g.setStroke(new BasicStroke(2.5f));
			g.draw(r);
			double s = w * 0.82;
			Graphics2D ic = (Graphics2D) g.create();
			ic.translate(-s / 2, -s / 2);
			ic.scale(s / 100, s / 100);
			kartIkonu(ic, no);
			ic.dispose();
		}

		/** Logodaki oyun kolu (200 x 130'luk alanda). */
		static void oyunKolu(Graphics2D g) {
			Area govde = new Area(new RoundRectangle2D.Double(30, 22, 140, 64, 60, 60));
			govde.add(new Area(new Ellipse2D.Double(10, 38, 70, 82)));
			govde.add(new Area(new Ellipse2D.Double(120, 38, 70, 82)));
			for (int i = 4; i >= 1; i--) {
				g.setColor(alfa(CAMGOBEGI, 34));
				g.setStroke(new BasicStroke(i * 5f));
				g.draw(govde);
			}
			g.setPaint(new GradientPaint(10, 22, MOR, 190, 120, PEMBE));
			g.fill(govde);
			g.setPaint(new GradientPaint(0, 22, alfa(Color.WHITE, 110), 0, 70, alfa(Color.WHITE, 0)));
			g.fill(govde);
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(3f));
			g.draw(govde);
			// Yön tuşu
			g.setColor(Color.WHITE);
			g.fill(new RoundRectangle2D.Double(38, 58, 40, 13, 5, 5));
			g.fill(new RoundRectangle2D.Double(51.5, 44.5, 13, 40, 5, 5));
			// Düğmeler
			Color[] renkler = { CAMGOBEGI, SARI, LIME, new Color(255, 90, 90) };
			double[][] yer = { { 148, 48 }, { 164, 64 }, { 132, 64 }, { 148, 80 } };
			for (int i = 0; i < 4; i++) {
				g.setColor(renkler[i]);
				g.fill(daire(yer[i][0], yer[i][1], 8));
				g.setColor(alfa(Color.WHITE, 200));
				g.setStroke(new BasicStroke(2f));
				g.draw(daire(yer[i][0], yer[i][1], 8));
			}
			g.setColor(alfa(Color.WHITE, 170));
			g.fill(new RoundRectangle2D.Double(86, 46, 12, 6, 6, 6));
			g.fill(new RoundRectangle2D.Double(102, 46, 12, 6, 6, 6));
		}

		/** Sohbetin başındaki görsele benzeyen kıvrılmış yılan maskotu (320 x 290). */
		static void maskot(Graphics2D g0, Palet p) {
			Graphics2D g = (Graphics2D) g0.create();
			Color murekkep = p.murekkep();
			BasicStroke kontur = new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
			g.setStroke(kontur);

			Path2D kuyruk = new Path2D.Double();
			kuyruk.moveTo(62, 250);
			kuyruk.curveTo(40, 262, 44, 278, 96, 276);
			kuyruk.lineTo(120, 256);
			kuyruk.closePath();
			dolKontur(g, kuyruk, p.ana().darker(), murekkep);
			dolKontur(g, new Ellipse2D.Double(44, 188, 244, 88), p.ana(), murekkep);
			g.setColor(alfa(p.koyu(), 140));
			g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			Path2D golge = new Path2D.Double();
			golge.moveTo(60, 222);
			golge.curveTo(96, 262, 236, 262, 272, 222);
			g.draw(golge);
			g.setStroke(kontur);
			dolKontur(g, new Ellipse2D.Double(80, 152, 184, 76), p.bas(), murekkep);

			Path2D boyun = new Path2D.Double();
			boyun.moveTo(190, 188);
			boyun.curveTo(262, 176, 250, 118, 180, 112);
			g.setColor(murekkep);
			g.setStroke(new BasicStroke(56f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(boyun);
			g.setColor(p.ana());
			g.setStroke(new BasicStroke(44f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(boyun);
			Path2D karin = new Path2D.Double();
			karin.moveTo(168, 192);
			karin.curveTo(226, 180, 218, 132, 170, 124);
			g.setColor(p.karin());
			g.setStroke(new BasicStroke(24f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
			g.draw(karin);

			// Baklavalar
			baklava(g, 92, 226, 16, 13, p.koyu(), murekkep, true);
			baklava(g, 146, 240, 18, 14, p.karin(), null, false);
			baklava(g, 212, 238, 17, 13, p.koyu(), murekkep, true);
			baklava(g, 126, 186, 14, 11, p.karin(), null, false);
			baklava(g, 186, 201, 14, 11, p.koyu(), murekkep, true);
			baklava(g, 228, 160, 11, 11, p.koyu(), null, true);

			// Dil
			Path2D dil = new Path2D.Double();
			dil.moveTo(96, 106);
			dil.curveTo(76, 116, 62, 124, 50, 138);
			dil.moveTo(64, 125);
			dil.curveTo(56, 124, 46, 126, 38, 128);
			g.setColor(murekkep);
			g.setStroke(new BasicStroke(11f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(dil);
			g.setColor(new Color(0xff3d6e));
			g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(dil);

			// Kafa
			g.setStroke(kontur);
			Path2D kafa = new Path2D.Double();
			kafa.moveTo(92, 86);
			kafa.curveTo(92, 40, 134, 12, 182, 14);
			kafa.curveTo(226, 16, 250, 48, 244, 86);
			kafa.curveTo(238, 120, 200, 134, 160, 132);
			kafa.curveTo(124, 132, 92, 122, 92, 86);
			kafa.closePath();
			dolKontur(g, kafa, p.bas(), murekkep);
			Path2D burun = new Path2D.Double();
			burun.moveTo(70, 92);
			burun.curveTo(70, 70, 94, 62, 116, 68);
			burun.curveTo(142, 74, 160, 92, 172, 96);
			burun.curveTo(160, 122, 128, 128, 104, 124);
			burun.curveTo(84, 120, 70, 110, 70, 92);
			burun.closePath();
			dolKontur(g, burun, p.ana(), murekkep);
			Path2D cene = new Path2D.Double();
			cene.moveTo(90, 113);
			cene.curveTo(112, 124, 146, 122, 168, 100);
			cene.curveTo(176, 110, 170, 122, 148, 128);
			cene.curveTo(126, 133, 102, 128, 90, 113);
			cene.closePath();
			g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			dolKontur(g, cene, p.karin(), murekkep);
			baklava(g, 152, 30, 12, 10, p.koyu(), null, true);
			baklava(g, 206, 30, 9, 8, p.koyu(), null, true);
			g.setColor(murekkep);
			g.fill(daire(88, 84, 4.5));
			g.fill(daire(108, 80, 4.5));

			// Gözler
			g.setStroke(kontur);
			dolKontur(g, new Ellipse2D.Double(113, 35, 46, 54), Color.WHITE, murekkep);
			dolKontur(g, new Ellipse2D.Double(175, 33, 58, 66), Color.WHITE, murekkep);
			g.setColor(new Color(0x12140f));
			g.fill(new Ellipse2D.Double(118, 48, 28, 36));
			g.fill(new Ellipse2D.Double(179, 47, 38, 46));
			g.setColor(Color.WHITE);
			g.fill(daire(127, 56, 5.5));
			g.fill(daire(191, 58, 7.5));
			g.fill(daire(138, 74, 2.5));
			g.fill(daire(205, 82, 3.5));
			g.dispose();
		}

		private static void dolKontur(Graphics2D g, Shape s, Color dolgu, Color kontur) {
			g.setColor(dolgu);
			g.fill(s);
			g.setColor(kontur);
			g.draw(s);
		}

		private static void baklava(Graphics2D g, double x, double y, double w, double h, Color renk, Color kontur,
				boolean dolu) {
			Path2D p = new Path2D.Double();
			p.moveTo(x, y);
			p.lineTo(x + w, y - h);
			p.lineTo(x + 2 * w, y);
			p.lineTo(x + w, y + h);
			p.closePath();
			if (dolu) {
				g.setColor(renk);
				g.fill(p);
				if (kontur != null) {
					g.setColor(kontur);
					g.setStroke(new BasicStroke(3f));
					g.draw(p);
				}
			} else {
				g.setColor(renk);
				g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g.draw(p);
			}
		}

		static void insan(Graphics2D g, double cx, double cy, double olcek, Color renk) {
			g.setColor(renk);
			g.fill(daire(cx, cy - 14 * olcek, 8 * olcek));
			g.fill(new Arc2D.Double(cx - 14 * olcek, cy - 3 * olcek, 28 * olcek, 30 * olcek, 0, 180, Arc2D.CHORD));
		}

		static void robot(Graphics2D g, double cx, double cy, double olcek, Color renk) {
			double s = olcek;
			g.setColor(renk);
			g.setStroke(new BasicStroke((float) (2.5 * s)));
			g.draw(new Line2D.Double(cx, cy - 22 * s, cx, cy - 14 * s));
			g.fill(daire(cx, cy - 23 * s, 3 * s));
			g.fill(new RoundRectangle2D.Double(cx - 15 * s, cy - 14 * s, 30 * s, 24 * s, 9 * s, 9 * s));
			g.setColor(MUREKKEP);
			g.fill(daire(cx - 6 * s, cy - 3 * s, 3.5 * s));
			g.fill(daire(cx + 6 * s, cy - 3 * s, 3.5 * s));
			g.fill(new RoundRectangle2D.Double(cx - 6 * s, cy + 4 * s, 12 * s, 2.5 * s, 2, 2));
		}

		static void kilit(Graphics2D g, double cx, double cy, double olcek) {
			double s = olcek;
			g.setColor(new Color(235, 230, 255));
			g.setStroke(new BasicStroke((float) (3.5 * s)));
			g.draw(new Arc2D.Double(cx - 8 * s, cy - 17 * s, 16 * s, 18 * s, 0, 180, Arc2D.OPEN));
			g.draw(new Line2D.Double(cx - 8 * s, cy - 8 * s, cx - 8 * s, cy - 3 * s));
			g.draw(new Line2D.Double(cx + 8 * s, cy - 8 * s, cx + 8 * s, cy - 3 * s));
			g.fill(new RoundRectangle2D.Double(cx - 12 * s, cy - 4 * s, 24 * s, 18 * s, 5 * s, 5 * s));
			g.setColor(MUREKKEP);
			g.fill(daire(cx, cy + 4 * s, 2.6 * s));
		}

		/** Oyun merkezindeki kart resimleri. */
		static void oyunIkonu(Graphics2D g0, String ad) {
			Graphics2D g = (Graphics2D) g0.create();
			cizgiKalinligi(g, 3.5);
			switch (ad) {
			case "yilan" -> {
				g.translate(2, 6);
				g.scale(96 / 320.0, 96 / 320.0);
				maskot(g, YESIL_YILAN);
			}
			case "hafiza" -> {
				Graphics2D a = (Graphics2D) g.create();
				a.translate(36, 54);
				a.rotate(-0.25);
				kucukKartArka(a, 46, 60);
				a.dispose();
				Graphics2D b = (Graphics2D) g.create();
				b.translate(64, 50);
				b.rotate(0.2);
				kucukKart(b, 10, 46, 60);
				b.dispose();
			}
			case "roket" -> {
				g.setColor(Color.WHITE);
				for (double[] y : new double[][] { { 14, 18 }, { 84, 26 }, { 20, 76 }, { 86, 82 }, { 76, 8 } }) {
					g.fill(yildiz(y[0], y[1], 4, 1.6, 4));
				}
				Graphics2D r = (Graphics2D) g.create();
				r.rotate(0.6, 50, 50);
				r.translate(10, 8);
				r.scale(0.8, 0.8);
				kartIkonu(r, 19);
				r.dispose();
			}
			case "tugla" -> {
				Color[] renk = { c(0xff5d5d), c(0xffd60a), c(0x2ec4b6) };
				for (int satir = 0; satir < 3; satir++) {
					for (int s = 0; s < 4; s++) {
						double x = 8 + s * 21 + (satir % 2) * 6;
						if (x > 74) {
							continue;
						}
						boya(g, new RoundRectangle2D.Double(x, 10 + satir * 13, 19, 10, 3, 3), renk[satir]);
					}
				}
				boya(g, daire(56, 64, 6), Color.WHITE);
				boya(g, new RoundRectangle2D.Double(28, 82, 44, 9, 9, 9), c(0x9b5de5));
				g.setColor(alfa(Color.WHITE, 160));
				cizgiKalinligi(g, 2);
				g.draw(new Line2D.Double(44, 52, 52, 60));
			}
			case "balon" -> {
				int[][] yer = { { -18, 8 }, { 18, 4 }, { 0, -6 } };
				Color[] renk = { c(0x4dabf7), c(0xffd60a), c(0xff4d8d) };
				for (int i = 0; i < 3; i++) {
					Graphics2D b = (Graphics2D) g.create();
					b.translate(yer[i][0] + 16, yer[i][1] + 10);
					b.scale(0.68, 0.68);
					b.setColor(c(0x7a7a9a));
					cizgiKalinligi(b, 2.5);
					b.draw(new CubicCurve2D.Double(50, 74, 40, 84, 60, 90, 48, 99));
					cizgiKalinligi(b, 3.5);
					boya(b, new Ellipse2D.Double(22, 6, 56, 66), renk[i]);
					b.setColor(new Color(255, 255, 255, 200));
					b.fill(dondur(new Ellipse2D.Double(32, 18, 10, 20), 0.4, 37, 28));
					b.dispose();
				}
			}
			case "labirent" -> {
				boya(g, new RoundRectangle2D.Double(10, 10, 80, 80, 10, 10), new Color(0, 60, 50, 140));
				g.setColor(Color.WHITE);
				cizgiKalinligi(g, 5);
				int[][] duvarlar = { { 10, 30, 50, 30 }, { 70, 10, 70, 50 }, { 30, 30, 30, 70 }, { 30, 70, 70, 70 },
						{ 50, 50, 90, 50 }, { 50, 50, 50, 70 }, { 10, 90, 90, 90 }, { 90, 10, 90, 70 },
						{ 10, 10, 90, 10 }, { 10, 10, 10, 90 } };
				for (int[] d : duvarlar) {
					g.draw(new Line2D.Double(d[0], d[1], d[2], d[3]));
				}
				boya(g, daire(20, 20, 5), c(0xffd60a));
				g.setColor(LIME);
				g.fill(new Polygon(new int[] { 80, 96, 80 }, new int[] { 72, 80, 88 }, 3));
			}
			case "blok" -> {
				Color[] renk = { c(0xff5d8f), c(0x4dabf7), c(0xffd60a), c(0x2fbf5b), c(0xff9f1c) };
				int[][] kareler = { { 1, 0, 0 }, { 2, 0, 0 }, { 2, 1, 0 }, { 0, 3, 1 }, { 1, 3, 1 }, { 2, 3, 1 },
						{ 3, 3, 2 }, { 3, 2, 2 }, { 0, 2, 3 }, { 1, 2, 3 }, { 4, 3, 4 }, { 4, 2, 4 }, { 3, 1, 4 } };
				for (int[] k : kareler) {
					double x = 8 + k[0] * 17, y = 18 + k[1] * 17;
					boya(g, new RoundRectangle2D.Double(x, y, 16, 16, 4, 4), renk[k[2]]);
					g.setColor(alfa(Color.WHITE, 140));
					g.fill(new Rectangle2D.Double(x + 3, y + 3, 10, 3));
				}
			}
			case "futbol" -> {
				g.setColor(Color.WHITE);
				cizgiKalinligi(g, 5);
				g.draw(new Line2D.Double(8, 70, 8, 22));
				g.draw(new Line2D.Double(8, 22, 92, 22));
				g.draw(new Line2D.Double(92, 22, 92, 70));
				g.setColor(alfa(Color.WHITE, 110));
				cizgiKalinligi(g, 1.5);
				for (int i = 1; i < 8; i++) {
					g.draw(new Line2D.Double(8 + i * 10.5, 22, 8 + i * 10.5, 70));
				}
				for (int i = 1; i < 5; i++) {
					g.draw(new Line2D.Double(8, 22 + i * 10, 92, 22 + i * 10));
				}
				cizgiKalinligi(g, 3.5);
				boya(g, daire(50, 70, 18), Color.WHITE);
				g.setColor(MUREKKEP);
				Path2D besgen = new Path2D.Double();
				for (int i = 0; i < 5; i++) {
					double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
					if (i == 0) {
						besgen.moveTo(50 + Math.cos(a) * 7, 70 + Math.sin(a) * 7);
					} else {
						besgen.lineTo(50 + Math.cos(a) * 7, 70 + Math.sin(a) * 7);
					}
				}
				besgen.closePath();
				g.fill(besgen);
				for (int i = 0; i < 5; i++) {
					double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
					g.draw(new Line2D.Double(50 + Math.cos(a) * 7, 70 + Math.sin(a) * 7, 50 + Math.cos(a) * 17,
							70 + Math.sin(a) * 17));
				}
			}
			case "xox" -> {
				g.setColor(alfa(Color.WHITE, 220));
				cizgiKalinligi(g, 4);
				g.draw(new Line2D.Double(38, 12, 38, 88));
				g.draw(new Line2D.Double(62, 12, 62, 88));
				g.draw(new Line2D.Double(12, 38, 88, 38));
				g.draw(new Line2D.Double(12, 62, 88, 62));
				g.setColor(c(0xff4d8d));
				cizgiKalinligi(g, 6);
				for (double[] x : new double[][] { { 25, 25 }, { 50, 50 }, { 75, 75 } }) {
					g.draw(new Line2D.Double(x[0] - 7, x[1] - 7, x[0] + 7, x[1] + 7));
					g.draw(new Line2D.Double(x[0] + 7, x[1] - 7, x[0] - 7, x[1] + 7));
				}
				g.setColor(c(0xffd60a));
				g.draw(daire(75, 25, 8));
				g.draw(daire(25, 75, 8));
			}
			case "araba" -> {
				g.setColor(alfa(Color.WHITE, 170));
				cizgiKalinligi(g, 4);
				g.draw(new Line2D.Double(4, 46, 18, 46));
				g.draw(new Line2D.Double(2, 58, 14, 58));
				g.draw(new Line2D.Double(6, 70, 16, 70));
				cizgiKalinligi(g, 3.5);
				Path2D govde = new Path2D.Double();
				govde.moveTo(20, 72);
				govde.lineTo(20, 56);
				govde.lineTo(36, 52);
				govde.lineTo(48, 36);
				govde.lineTo(72, 36);
				govde.lineTo(84, 52);
				govde.lineTo(94, 56);
				govde.lineTo(94, 72);
				govde.closePath();
				boya(g, govde, c(0xff3b3b));
				boya(g, new Polygon(new int[] { 50, 58, 58, 41 }, new int[] { 40, 40, 52, 52 }, 4), c(0xbfe9ff));
				boya(g, new Polygon(new int[] { 63, 70, 79, 63 }, new int[] { 40, 40, 52, 52 }, 4), c(0xbfe9ff));
				g.setColor(c(0xffd60a));
				g.fill(new Rectangle2D.Double(88, 58, 6, 5));
				for (double x : new double[] { 36, 78 }) {
					boya(g, daire(x, 72, 10), c(0x2b2b3a));
					g.setColor(c(0xc8c8d8));
					g.fill(daire(x, 72, 4));
				}
			}
			default -> {
			}
			}
			g.dispose();
		}

		static void kucukKartArka(Graphics2D g, double w, double h) {
			RoundRectangle2D r = new RoundRectangle2D.Double(-w / 2, -h / 2, w, h, 10, 10);
			g.setPaint(new GradientPaint((float) (-w / 2), (float) (-h / 2), new Color(0x4a26c9), (float) (w / 2),
					(float) (h / 2), new Color(0x1a0f58)));
			g.fill(r);
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(2.5f));
			g.draw(r);
			Font f = kalin((float) (h * 0.5));
			TextLayout tl = new TextLayout("?", f, g.getFontRenderContext());
			isikliYazi(g, "?", f, 0, tl.getBounds().getHeight() / 2, Color.WHITE, PEMBE, 0.8);
		}
	}

	// =====================================================================
	// PROGRAMIN BAŞLADIĞI YER
	// =====================================================================
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new MiniOyunMerkezi().setVisible(true));
	}
}
