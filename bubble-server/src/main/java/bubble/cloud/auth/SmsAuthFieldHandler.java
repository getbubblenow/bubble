package bubble.cloud.auth;

import org.cobbzilla.util.string.ValidationRegexes;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.cobbzilla.util.daemon.ZillaRuntime.empty;
import static org.cobbzilla.util.string.LocaleUtil.isValidCountryCode;

public class SmsAuthFieldHandler implements AuthFieldHandler {

    @Override public List<ConstraintViolationBean> validate(String val) {
        if (empty(val)) {
            return singleError("err.phone.required", "empty phone number");
        }
        final int colonPos = val.indexOf(":");
        if (colonPos == -1 || colonPos == val.length()-1) {
            return singleError("err.phone.invalid", "invalid phone number, use format 'country:number'");
        }

        // validate country
        final String country = val.substring(0, colonPos);
        if (country.length() != 2) {
            return singleError("err.country.invalid", "invalid country code, use ISO 2-letter code", country);
        }
        if (!isValidCountryCode(country)) {
            return singleError("err.country.invalid", "invalid country code, use a valid ISO code", country);
        }

        // remove all non-digit characters
        final String phone = formatPhoneForCountry(country, val.substring(colonPos+1));

        // validate phone based on country
        if (!getPhonePattern(country).matcher(phone).matches()) {
            return singleError("err.phone.invalid", "invalid phone number: "+phone, val);
        }

        return Collections.emptyList();
    }

    public static String formatPhoneForCountry(String country, String phone) {
        phone = phone.replaceAll("[\\D]+", ""); // remove all non-digits
        switch (country) {
            case "US":
                // If it is 10 digits and starts with a 1, drop the leading 1
                if (phone.length() == 11 && phone.charAt(0) == '1') return phone.substring(1);
                return phone;
            default:
                return phone;
        }
    }

    private Pattern getPhonePattern(String country) {
        if (ValidationRegexes.PHONE_PATTERNS.containsKey(country)) return ValidationRegexes.PHONE_PATTERNS.get(country);
        return ValidationRegexes.DEFAULT_PHONE_PATTERN;  // just checks that it starts and ends with a digit, and is 9+ digits long
    }

    public static final int UNMASKED_LEN = 4;

    @Override public String mask(String val) {

        final int colonPos = val.indexOf(":");

        if (colonPos == -1 || colonPos == val.length()-1) return "*".repeat(val.length());
        final String country = val.substring(0, colonPos);
        final String phone = val.substring(colonPos+1);

        if (phone.length() < UNMASKED_LEN) return  country+":"+"*".repeat(phone.length());

        return country + ":" + phone.substring(0, phone.length()-UNMASKED_LEN).replaceAll("\\d", "*")
                + phone.substring(phone.length()-UNMASKED_LEN);
    }

}
