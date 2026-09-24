package com.claimpilot.samples;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceCharacteristicsDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Generates the fictional demo documents: two group benefit policies (one in French), a
 * physiotherapy receipt, and the second-plan claim form. Every company and person is invented.
 * <p>
 * Regenerate from the backend folder:
 * <pre>
 * ./mvnw -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * java -cp "target/test-classes:target/classes:$(cat target/cp.txt)" com.claimpilot.samples.SampleDocuments ..
 * </pre>
 */
public final class SampleDocuments {

    public static final String SPOUSE_POLICY = "cedarview-policy-marc-gagnon.pdf";
    public static final String OWN_POLICY = "harbourline-police-fiona-tremblay-fr.pdf";
    public static final String RECEIPT_PDF = "physio-receipt-2026-03-05.pdf";
    public static final String RECEIPT_PNG = "physio-receipt-2026-03-05.png";
    public static final String CLAIM_FORM = "cedarview-secondary-claim-form.pdf";
    public static final String FRENCH_CLAIM_FORM = "harbourline-demande-de-remboursement.pdf";
    public static final String SPOUSE_POLICY_SCANNED = "cedarview-policy-marc-gagnon-scanned.pdf";
    public static final String RECEIPT_SCANNED = "physio-receipt-2026-03-05-scanned.pdf";

    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final float MARGIN = 60;

    private SampleDocuments() {
    }

    /** Writes the samples to {@code <root>/sample-docs} and the form to the backend resources. */
    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : "..");
        Path samples = root.resolve("sample-docs");
        Files.createDirectories(samples);
        Files.write(samples.resolve(SPOUSE_POLICY), spousePolicy());
        Files.write(samples.resolve(OWN_POLICY), ownPolicyFrench());
        Files.write(samples.resolve(RECEIPT_PDF), receiptPdf());
        Files.write(samples.resolve(RECEIPT_PNG), receiptPng());
        Files.write(samples.resolve(SPOUSE_POLICY_SCANNED), scanned(spousePolicy()));
        Files.write(samples.resolve(RECEIPT_SCANNED), scanned(receiptPdf()));
        Path forms = root.resolve("backend/src/main/resources/forms");
        Files.createDirectories(forms);
        Files.write(forms.resolve(CLAIM_FORM), claimForm());
        Files.write(samples.resolve(CLAIM_FORM), claimForm());
        Files.write(forms.resolve(FRENCH_CLAIM_FORM), frenchClaimForm());
        Files.write(samples.resolve(FRENCH_CLAIM_FORM), frenchClaimForm());
        System.out.println("Sample documents written to " + samples.toAbsolutePath().normalize());
    }

    // ---------------------------------------------------------------- policies

    /** Marc Gagnon's plan (Fiona's spouse), in English: the plan claimed on second. */
    public static byte[] spousePolicy() {
        return textPdf(List.of(
                List.of(
                        h("CEDARVIEW ASSURANCE"),
                        p("Group Benefits Booklet - Extended Health and Dental Care"),
                        note("Fictional document created for a software demonstration. Cedarview Assurance is not a real company."),
                        gap(),
                        h2("Your plan"),
                        p("Plan sponsor: Rive-Nord Logistics Ltd."),
                        p("Group policy number: CV-88213-02"),
                        p("Plan member: Marc Gagnon"),
                        p("Certificate number: 55190336"),
                        p("Coverage: member and spouse"),
                        p("Effective date: January 1, 2026"),
                        gap(),
                        h2("Contact us"),
                        p("Member Services: 1-800-555-0199"),
                        p("Hours: Monday to Friday, 8 a.m. to 8 p.m. (Eastern Time)"),
                        p("Online: member.cedarview.example and the Cedarview Benefits app"),
                        p("Mail paper claims to: Cedarview Assurance, Claims Services, P.O. Box 4410, Station A, Montreal, QC H3C 0A0")),
                List.of(
                        h2("Section 3 - Paramedical practitioners"),
                        p("Eligible expenses for the following licensed practitioners are reimbursed at 80%."),
                        p("Physiotherapist: up to $600 per calendar year per person. No physician referral is required."),
                        p("Massage therapist: up to $400 per calendar year per person. A physician's referral is required "
                                + "before the first treatment of each calendar year."),
                        p("Chiropractor: up to $500 per calendar year per person, including one X-ray per year."),
                        p("Practitioners must be members in good standing of the professional order of their province. "
                                + "Treatments by a kinesiologist may be considered when they are part of a rehabilitation program."),
                        gap(),
                        h2("Section 4 - Dental care"),
                        p("Basic services (exams, cleanings, fillings) are reimbursed at 80%, up to $1,500 per calendar year "
                                + "per person, based on the Quebec dental fee guide of the current year."),
                        p("For any treatment plan over $500, submit a predetermination before treatment begins."),
                        gap(),
                        h2("Section 5 - Prescription drugs"),
                        p("Drugs that require a prescription and have a Drug Identification Number (DIN) are reimbursed at 80%. "
                                + "Show your Cedarview drug card at the pharmacy for direct billing.")),
                List.of(
                        h2("Section 6 - Submitting a claim"),
                        p("Claims must be received within 12 months of the date the expense was incurred. "
                                + "Claims received after this deadline will not be paid."),
                        p("Submit claims in the Cedarview Benefits app or at member.cedarview.example, or mail the "
                                + "Supplementary Health Claim form to the address on page 1."),
                        p("Include the original itemized receipt showing the patient's name, the practitioner's name and "
                                + "licence number, the date and type of service, and the amount charged."),
                        gap(),
                        h2("Section 7 - Coordination of benefits"),
                        p("When a person is covered by more than one group plan, the plans coordinate so that the total paid "
                                + "does not exceed 100% of the expense. A person's own employer plan pays first. "
                                + "For a spouse, the spouse's own plan pays first and this plan pays second."),
                        p("To claim the balance under this plan, submit the claim with the Explanation of Benefits (claim "
                                + "statement) from the first plan, or a receipt showing the amount the first plan paid by "
                                + "direct billing. The second claim must also be received within 12 months of the date of service."))));
    }

    /** Fiona Tremblay's own plan, in French: the plan that pays first. */
    public static byte[] ownPolicyFrench() {
        return textPdf(List.of(
                List.of(
                        h("HARBOURLINE VIE"),
                        p("Assurance collective - Sommaire des garanties"),
                        note("Document fictif créé pour une démonstration logicielle. Harbourline Vie n'est pas une vraie compagnie."),
                        gap(),
                        h2("Votre régime"),
                        p("Preneur (employeur) : Atelier Boréal Design inc."),
                        p("Numéro de police collective : HL-204518"),
                        p("Adhérente : Fiona Tremblay"),
                        p("Numéro de certificat : 7730142"),
                        gap(),
                        h2("Nous joindre"),
                        p("Service à la clientèle : 1-888-555-0123"),
                        p("Heures : du lundi au vendredi, de 8 h à 18 h (heure de l'Est)")),
                List.of(
                        h2("Soins paramédicaux"),
                        p("La physiothérapie est remboursée à 70 %, jusqu'à 500 $ par année civile et par personne. "
                                + "Le paiement direct est offert dans les cliniques participantes."),
                        p("La massothérapie est remboursée à 70 %, jusqu'à 300 $ par année civile."),
                        gap(),
                        h2("Réclamations"),
                        p("Toute demande de règlement doit être reçue dans les 12 mois suivant la date du service."),
                        gap(),
                        h2("Coordination des prestations"),
                        p("Pour l'adhérente, le présent régime paie en premier. Le solde peut être réclamé au régime du "
                                + "conjoint avec le relevé de prestations de Harbourline Vie."))));
    }

    // ---------------------------------------------------------------- receipt

    public static byte[] receiptPdf() {
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(420, 560));
            pdf.addPage(page);
            try (PDPageContentStream out = new PDPageContentStream(pdf, page)) {
                float y = 520;
                y = line(out, BOLD, 15, 30, y, "CLINIQUE PHYSIO PLATEAU");
                y = line(out, REGULAR, 9, 30, y, "1234 avenue du Mont-Royal Est, Montreal, QC H2J 1Y5");
                y = line(out, REGULAR, 9, 30, y, "Tel. 514-555-0177");
                y = line(out, REGULAR, 8, 30, y, "Fictional receipt created for a software demonstration.");
                y -= 16;
                y = line(out, BOLD, 12, 30, y, "RECEIPT / RECU");
                y = line(out, REGULAR, 10, 30, y, "Receipt no.: R-2026-0318");
                y = line(out, REGULAR, 10, 30, y, "Date of service: March 5, 2026");
                y = line(out, REGULAR, 10, 30, y, "Patient: Fiona Tremblay");
                y = line(out, REGULAR, 10, 30, y, "Practitioner: Julie Bergeron, physiotherapist, licence no. 12345");
                y -= 12;
                y = line(out, REGULAR, 10, 30, y, "Service: Physiotherapy - follow-up treatment (45 min)");
                y = line(out, BOLD, 10, 30, y, "Total charged: $120.00");
                y = line(out, REGULAR, 10, 30, y, "Paid by Harbourline Vie (direct billing): $84.00");
                y = line(out, REGULAR, 10, 30, y, "Paid by patient: $36.00");
                y -= 12;
                line(out, REGULAR, 9, 30, y, "Thank you! Keep this receipt for your second insurance plan.");
            }
            return save(pdf);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** The same receipt as a photo-like image, to demonstrate text recognition. */
    public static byte[] receiptPng() {
        try (PDDocument pdf = org.apache.pdfbox.Loader.loadPDF(receiptPdf())) {
            BufferedImage image = new PDFRenderer(pdf).renderImageWithDPI(0, 200, ImageType.RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * The same document as a scan: every page becomes a picture and the PDF has no text layer,
     * like a paper booklet run through a scanner.
     */
    public static byte[] scanned(byte[] pdfBytes) {
        try (PDDocument source = org.apache.pdfbox.Loader.loadPDF(pdfBytes); PDDocument scan = new PDDocument()) {
            PDFRenderer renderer = new PDFRenderer(source);
            for (int i = 0; i < source.getNumberOfPages(); i++) {
                PDRectangle size = source.getPage(i).getMediaBox();
                BufferedImage image = renderer.renderImageWithDPI(i, 200, ImageType.GRAY);
                PDPage page = new PDPage(size);
                scan.addPage(page);
                var picture = org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(scan, image);
                try (PDPageContentStream out = new PDPageContentStream(scan, page)) {
                    out.drawImage(picture, 0, 0, size.getWidth(), size.getHeight());
                }
            }
            return save(scan);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    // ---------------------------------------------------------------- claim form

    /** The Cedarview second-plan claim form (English). */
    public static byte[] claimForm() {
        return fillableForm(List.of(
                "CEDARVIEW ASSURANCE",
                "Supplementary Health Claim - Second Plan",
                "Fictional form created for a software demonstration. Cedarview Assurance is not a real company."),
                List.of(
                        section("Part A - Plan member on this Cedarview plan"),
                        field("txtField_01", "Plan member's full name"),
                        field("txtField_02", "Group policy number"),
                        field("txtField_03", "Certificate number"),
                        field("txtField_04", "Plan sponsor (employer)"),
                        section("Part B - Patient"),
                        field("txtField_05", "Patient's full name"),
                        field("txtField_06", "Patient's date of birth (YYYY-MM-DD)"),
                        field("txtField_07", "Patient's relationship to the plan member"),
                        field("txtField_08", "Patient's mailing address"),
                        section("Part C - Other plan (paid first)"),
                        field("txtField_09", "Name of the other insurance company"),
                        field("txtField_10", "Other plan's group policy number"),
                        field("txtField_11", "Other plan's certificate or ID number"),
                        section("Part D - Expense"),
                        field("txtField_12", "Name of clinic or practitioner"),
                        field("txtField_13", "Date of service (YYYY-MM-DD)"),
                        field("txtField_14", "Type of service"),
                        field("txtField_15", "Total amount charged ($)"),
                        field("txtField_16", "Amount paid by the other plan ($)"),
                        field("txtField_17", "Amount claimed from Cedarview ($)"),
                        section("Part E - Payment and declaration"),
                        field("txtField_19", "Bank transit and account number for direct deposit"),
                        checkbox("chkField_01", "I declare that the information on this claim is true and complete"),
                        field("sigField_01", "Signature of plan member"),
                        field("txtField_18", "Date signed (YYYY-MM-DD)")));
    }

    /**
     * A second fillable form, in French, from the fictional Harbourline Vie: shows that mapping by
     * label works whatever the language and field names (here f01, f02...).
     */
    public static byte[] frenchClaimForm() {
        return fillableForm(List.of(
                "HARBOURLINE VIE",
                "Demande de remboursement - soins de sante complementaires",
                "Formulaire fictif cree pour une demonstration logicielle. Harbourline Vie n'existe pas."),
                List.of(
                        section("Section 1 - Adherent"),
                        field("f01", "Nom de l'adh\u00e9rent"),
                        field("f02", "Num\u00e9ro de police collective"),
                        field("f03", "Num\u00e9ro de certificat"),
                        section("Section 2 - Patient"),
                        field("f04", "Nom du patient"),
                        field("f05", "Date de naissance du patient (AAAA-MM-JJ)"),
                        field("f06", "Lien avec l'adh\u00e9rent"),
                        field("f07", "Adresse du patient"),
                        field("f08", "T\u00e9l\u00e9phone du patient"),
                        section("Section 3 - Autre regime"),
                        field("f09", "Nom de l'autre assureur"),
                        field("f10", "Num\u00e9ro de police de l'autre r\u00e9gime"),
                        section("Section 4 - Frais"),
                        field("f11", "Nom du fournisseur de soins"),
                        field("f12", "Num\u00e9ro du re\u00e7u"),
                        field("f13", "Date du service (AAAA-MM-JJ)"),
                        field("f14", "Type de soin"),
                        field("f15", "Montant factur\u00e9 ($)"),
                        field("f16", "Montant pay\u00e9 par l'autre r\u00e9gime ($)"),
                        field("f17", "Montant r\u00e9clam\u00e9 ($)"),
                        section("Section 5 - Declaration"),
                        checkbox("f18", "J'atteste que les renseignements fournis sont exacts"),
                        field("f19", "Signature de l'adh\u00e9rent"),
                        field("f20", "Date de la signature (AAAA-MM-JJ)")));
    }

    /** One line of a generated form: a section title, a text field or a checkbox. */
    private record FormLine(String name, String label, boolean checkbox) {
    }

    private static FormLine section(String title) {
        return new FormLine(null, title, false);
    }

    private static FormLine field(String name, String label) {
        return new FormLine(name, label, false);
    }

    private static FormLine checkbox(String name, String label) {
        return new FormLine(name, label, true);
    }

    /**
     * A one-page fillable form. Field names are deliberately meaningless (txtField_07, f07), as on
     * many real forms; only the tooltips say what each field is.
     */
    private static byte[] fillableForm(List<String> header, List<FormLine> lines) {
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            pdf.addPage(page);
            PDAcroForm form = new PDAcroForm(pdf);
            pdf.getDocumentCatalog().setAcroForm(form);
            PDResources resources = new PDResources();
            // A font of its own: PDFBox clears a shared font dictionary when another document closes.
            resources.put(COSName.HELV, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            form.setDefaultResources(resources);
            form.setDefaultAppearance("/Helv 10 Tf 0 g");
            form.setNeedAppearances(true);

            List<Object[]> layout = new ArrayList<>();
            try (PDPageContentStream out = new PDPageContentStream(pdf, page)) {
                float y = 745;
                y = line(out, BOLD, 15, MARGIN, y, header.get(0));
                y = line(out, BOLD, 12, MARGIN, y, header.get(1));
                y = line(out, REGULAR, 8, MARGIN, y, header.get(2));
                y -= 6;
                for (FormLine l : lines) {
                    if (l.name() == null) {
                        y = section(out, y, l.label());
                    } else if (l.checkbox()) {
                        y = checkboxRow(out, layout, y, l.name(), l.label());
                    } else {
                        y = row(out, layout, y, l.name(), l.label());
                    }
                }
            }
            for (Object[] field : layout) {
                if ((Boolean) field[3]) {
                    addCheckbox(pdf, form, page, (String) field[0], (String) field[1], (PDRectangle) field[2]);
                } else {
                    addTextField(form, page, (String) field[0], (String) field[1], (PDRectangle) field[2]);
                }
            }
            return save(pdf);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static float section(PDPageContentStream out, float y, String title) throws IOException {
        return line(out, BOLD, 10.5f, MARGIN, y - 8, title);
    }

    private static float row(PDPageContentStream out, List<Object[]> layout, float y, String name, String label)
            throws IOException {
        text(out, REGULAR, 9, MARGIN, y - 12, label);
        layout.add(new Object[]{name, label, new PDRectangle(300, y - 17, 252, 16), false});
        return y - 21;
    }

    private static float checkboxRow(PDPageContentStream out, List<Object[]> layout, float y, String name,
                                     String label) throws IOException {
        layout.add(new Object[]{name, label, new PDRectangle(MARGIN, y - 16, 12, 12), true});
        text(out, REGULAR, 9, MARGIN + 18, y - 13, label);
        return y - 21;
    }

    private static void addTextField(PDAcroForm form, PDPage page, String name, String label, PDRectangle rect)
            throws IOException {
        PDTextField field = new PDTextField(form);
        field.setPartialName(name);
        field.setAlternateFieldName(label);
        field.setDefaultAppearance("/Helv 9 Tf 0 g");
        form.getFields().add(field);
        PDAnnotationWidget widget = field.getWidgets().getFirst();
        widget.setRectangle(rect);
        widget.setPage(page);
        widget.setPrinted(true);
        widget.setAppearanceCharacteristics(border());
        page.getAnnotations().add(widget);
        field.setValue("");
    }

    private static void addCheckbox(PDDocument pdf, PDAcroForm form, PDPage page, String name, String label, PDRectangle rect)
            throws IOException {
        PDCheckBox box = new PDCheckBox(form);
        box.setPartialName(name);
        box.setAlternateFieldName(label);
        form.getFields().add(box);
        PDAnnotationWidget widget = box.getWidgets().getFirst();
        widget.setRectangle(rect);
        widget.setPage(page);
        widget.setPrinted(true);
        widget.setAppearanceCharacteristics(border());
        PDAppearanceDictionary appearance = new PDAppearanceDictionary();
        PDAppearanceEntry normal = new PDAppearanceEntry(new org.apache.pdfbox.cos.COSDictionary());
        normal.getCOSObject().setItem(COSName.getPDFName("Yes"), checkboxAppearance(pdf, rect, true));
        normal.getCOSObject().setItem(COSName.Off, checkboxAppearance(pdf, rect, false));
        appearance.setNormalAppearance(normal);
        widget.setAppearance(appearance);
        widget.getCOSObject().setName(COSName.AS, "Off");
        page.getAnnotations().add(widget);
    }

    private static PDAppearanceStream checkboxAppearance(PDDocument pdf, PDRectangle rect, boolean on)
            throws IOException {
        PDAppearanceStream stream = new PDAppearanceStream(pdf);
        stream.setBBox(new PDRectangle(rect.getWidth(), rect.getHeight()));
        stream.setResources(new PDResources());
        try (PDPageContentStream out = new PDPageContentStream(pdf, stream)) {
            out.setStrokingColor(0.3f, 0.3f, 0.3f);
            out.addRect(0.5f, 0.5f, rect.getWidth() - 1, rect.getHeight() - 1);
            out.stroke();
            if (on) {
                out.moveTo(2.5f, rect.getHeight() / 2);
                out.lineTo(rect.getWidth() / 2.5f, 2.5f);
                out.lineTo(rect.getWidth() - 2.5f, rect.getHeight() - 2.5f);
                out.stroke();
            }
        }
        return stream;
    }

    private static PDAppearanceCharacteristicsDictionary border() {
        PDAppearanceCharacteristicsDictionary chars =
                new PDAppearanceCharacteristicsDictionary(new org.apache.pdfbox.cos.COSDictionary());
        chars.setBorderColour(new PDColor(new float[]{0.55f, 0.6f, 0.58f}, PDDeviceRGB.INSTANCE));
        return chars;
    }

    // ---------------------------------------------------------------- text layout

    private record Para(PDType1Font font, float size, String text, float before) {
    }

    private static Para h(String text) {
        return new Para(BOLD, 16, text, 0);
    }

    private static Para h2(String text) {
        return new Para(BOLD, 12, text, 6);
    }

    private static Para p(String text) {
        return new Para(REGULAR, 10.5f, text, 3);
    }

    private static Para note(String text) {
        return new Para(REGULAR, 8.5f, text, 2);
    }

    private static Para gap() {
        return new Para(REGULAR, 10, "", 6);
    }

    /** One list of paragraphs per page, wrapped to the page width. */
    private static byte[] textPdf(List<List<Para>> pages) {
        try (PDDocument pdf = new PDDocument()) {
            for (List<Para> paragraphs : pages) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                pdf.addPage(page);
                try (PDPageContentStream out = new PDPageContentStream(pdf, page)) {
                    float y = PDRectangle.LETTER.getHeight() - MARGIN;
                    float width = PDRectangle.LETTER.getWidth() - 2 * MARGIN;
                    for (Para para : paragraphs) {
                        y -= para.before();
                        for (String wrapped : wrap(para.text(), para.font(), para.size(), width)) {
                            y = line(out, para.font(), para.size(), MARGIN, y, wrapped);
                        }
                    }
                    text(out, REGULAR, 8, MARGIN, 40, "Page " + (pdf.getPages().indexOf(page) + 1));
                }
            }
            return save(pdf);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static List<String> wrap(String text, PDType1Font font, float size, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.getStringWidth(candidate) / 1000 * size > width && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        lines.add(current.toString());
        return lines;
    }

    private static float line(PDPageContentStream out, PDType1Font font, float size, float x, float y, String text)
            throws IOException {
        float next = y - size * 1.45f;
        if (!text.isEmpty()) {
            text(out, font, size, x, next, text);
        }
        return next;
    }

    private static void text(PDPageContentStream out, PDType1Font font, float size, float x, float y, String text)
            throws IOException {
        out.beginText();
        out.setFont(font, size);
        out.newLineAtOffset(x, y);
        out.showText(text);
        out.endText();
    }

    private static byte[] save(PDDocument pdf) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdf.save(out);
        return out.toByteArray();
    }
}
