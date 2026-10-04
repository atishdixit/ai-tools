import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Writes company-data/employee-handbook.pdf, a small PDF with a real text layer, used to demonstrate PDF indexing.
 * Run it with {@code tools\generate-handbook.bat}, which copies PDFBox from the project's dependencies and launches this
 * file. Only characters of the standard Helvetica font are used, so prices are written "Rs".
 */
public class GenerateHandbookPdf {

    private static final String[][] PAGES = {
            {"Zenith Cloudworks Employee Handbook", "",
                    "1. Code of conduct",
                    "Treat colleagues, customers and partners with respect. Harassment and discrimination of any kind are not tolerated.",
                    "Report concerns to your manager or to the Head of People Operations, Farah Sheikh. Reports can also be sent in confidence to",
                    "ethics@zenithcloudworks.example. Retaliation against anyone who reports a concern in good faith is a disciplinary offence.",
                    "",
                    "2. Confidential information",
                    "Customer data and internal financial information must not be shared outside the company or stored on personal devices.",
                    "Use only company-approved tools for work files."},
            {"3. Travel and expenses", "",
                    "Flights: book economy class for flights shorter than 6 hours. Business class is allowed only for flights of 6 hours or",
                    "longer and needs the approval of the Chief Financial Officer.",
                    "Hotels: the limit is Rs 7,000 per night in metro cities and Rs 4,500 per night elsewhere.",
                    "Meals: the daily meal allowance while travelling is Rs 1,200.",
                    "Claims: submit expense claims within 30 days of the expense, with receipts attached. Late claims may be rejected.",
                    "Approvals: a manager can approve claims up to Rs 10,000. Claims above Rs 10,000 also need approval from the Head of Finance.",
                    "Reimbursement is paid with the next monthly salary."},
            {"4. Equipment and IT", "",
                    "Every employee receives a company laptop. Laptops are replaced every 3 years.",
                    "Lost or stolen equipment must be reported to the IT helpdesk, helpdesk@zenithcloudworks.example, within 24 hours.",
                    "Install only approved software. Use the company VPN when connecting from outside the office.",
                    "",
                    "5. Dress code",
                    "The dress code is business casual. Fridays are casual days. Customer-facing staff should dress formally when visiting",
                    "a customer site."}};

    public static void main(String[] args) throws Exception {
        Path target = Path.of(args.length > 0 ? args[0] : "company-data/employee-handbook.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            for (String[] lines : PAGES) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    float y = 780;
                    boolean first = true;
                    for (String line : lines) {
                        boolean heading = first || line.matches("^\\d\\. .*");
                        cs.beginText();
                        cs.setFont(heading ? bold : regular, heading ? 14 : 11);
                        cs.newLineAtOffset(56, y);
                        cs.showText(line);
                        cs.endText();
                        y -= heading ? 26 : 17;
                        first = false;
                    }
                }
            }
            doc.save(target.toFile());
        }
        System.out.println("Wrote " + target.toAbsolutePath());
    }
}
