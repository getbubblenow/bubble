package bubble.rule.bblock.spec;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class BlockListTest {

    public static final String BLOCK = BlockDecisionType.block.name();
    public static final String ALLOW = BlockDecisionType.allow.name();
    public static final String FILTER = BlockDecisionType.filter.name();

    public static final String[][] BLOCK_TESTS = {
              // rule                  // fqdn                 // path          // expected decision

            // bare hosts example (ala EasyList)
            {"example.com",           "example.com",          "/some_path",      BLOCK},
            {"example.com",           "foo.example.com",      "/some_path",      BLOCK},
            {"example.com",           "example.org",          "/some_path",      ALLOW},

            // block example.com and all subdomains
            {"||example.com^",        "example.com",          "/some_path",      BLOCK},
            {"||example.com^",        "foo.example.com",      "/some_path",      BLOCK},
            {"||example.com^",        "example.org",          "/some_path",      ALLOW},

            // block exact string
            {"|example.com/|",        "example.com",          "/",               BLOCK},
            {"|example.com/|",        "example.com",          "/some_path",      ALLOW},
            {"|example.com/|",        "foo.example.com",      "/some_path",      ALLOW},

            // block example.com, but not foo.example.com or bar.example.com
            {"||example.com^$domain=~foo.example.com|~bar.example.com",
                                      "example.com",          "/some_path",      BLOCK},
            {"||example.com^$domain=~foo.example.com|~bar.example.com",
                                      "foo.example.com",      "/some_path",      ALLOW},
            {"||example.com^$domain=~foo.example.com|~bar.example.com",
                                      "bar.example.com",      "/some_path",      ALLOW},
            {"||example.com^$domain=~foo.example.com|~bar.example.com",
                                      "baz.example.com",      "/some_path",      BLOCK},

            // block images and scripts on example.com, but not foo.example.com or bar.example.com
            {"||example.com^$image,script,domain=~foo.example.com|~bar.example.com",
                                      "example.com",            "/some_path",      ALLOW},

            // test image blocking
            {"||example.com^$image,script,domain=~foo.example.com|~bar.example.com",
                                      "example.com",            "/some_path.png",  BLOCK},
            {"||example.com^$image,script,domain=~foo.example.com|~bar.example.com",
                                      "foo.example.com",        "/some_path.png",  ALLOW},
            {"||example.com^$image,script,domain=~foo.example.com|~bar.example.com",
                                      "bar.example.com",        "/some_path.png",  ALLOW},
            {"||example.com^$image,script,domain=~foo.example.com|~bar.example.com",
                                      "baz.example.com",         "/some_path.png", BLOCK},

            // test script blocking
            {"||example.com^$image,script,domain=~foo.example.com|~bar.example.com",
                                      "example.com",            "/some_path.js",   BLOCK},
            {"||example.com^$image,script,domain=~foo.example.com|~bar.example.com",
                                      "foo.example.com",        "/some_path.js",   ALLOW},
            {"||example.com^$image,script,domain=~foo.example.com|~bar.example.com",
                                      "bar.example.com",        "/some_path.js",   ALLOW},
            {"||example.com^$image,script,domain=~foo.example.com|~bar.example.com",
                                      "baz.example.com",         "/some_path.js",  BLOCK},

            // test stylesheet blocking
            {"||example.com^stylesheet,domain=~foo.example.com|~bar.example.com",
                    "example.com",            "/some_path.css",  BLOCK},
            {"||example.com^$stylesheet,domain=~foo.example.com|~bar.example.com",
                    "foo.example.com",        "/some_path.css",  ALLOW},
            {"||example.com^$stylesheet,domain=~foo.example.com|~bar.example.com",
                    "bar.example.com",        "/some_path.css",  ALLOW},
            {"||example.com^$stylesheet,domain=~foo.example.com|~bar.example.com",
                    "baz.example.com",         "/some_path.css", BLOCK},


            // path matching
            {"/foo",                  "example.com",          "/some_path",      ALLOW},
            {"/foo",                  "example.com",          "/foo",            BLOCK},
            {"/foo",                  "example.com",          "/foo/bar",        BLOCK},

            // path matching with wildcard
            {"/foo/*/img",            "example.com",          "/some_path",      ALLOW},
            {"/foo/*/img",            "example.com",          "/foo",            ALLOW},
            {"/foo/*/img",            "example.com",          "/foo/img",        ALLOW},
            {"/foo/*/img",            "example.com",          "/foo/x/img",      BLOCK},
            {"/foo/*/img",            "example.com",          "/foo/x/img.png",  ALLOW},
            {"/foo/*/img",            "example.com",          "/foo/x/y/z//img", BLOCK},

            // path matching with regex
            {"/foo/(apps|ads)/img.+/",  "example.com",          "/foo/x/y/z//img",      ALLOW},
            {"/foo/(apps|ads)/img.+/",  "example.com",          "/foo/apps/img.png",    BLOCK},
            {"/foo/(apps|ads)/img.+/",  "example.com",          "/foo/ads/img.png",     BLOCK},
            {"/foo/(apps|ads)/img.+/",  "example.com",          "/foo/bar/ads/img.png", ALLOW},

            {"/(apps|ads)\\.example\\.(com|org)/",
                                        "example.com",          "/ad.png",              ALLOW},
            {"/(apps|ads)\\.example\\.(com|org)/",
                                        "ads.example.com",      "/ad.png",              BLOCK},
            {"/(apps|ads)\\.example\\.(com|org)/",
                                        "apps.example.com",     "/ad.png",              BLOCK},
            {"/(apps|ads)\\.example\\.(com|org)/",
                                        "ads.example.org",      "/ad.png",              BLOCK},
            {"/(apps|ads)\\.example\\.(com|org)/",
                                        "apps.example.org",     "/ad.png",              BLOCK},
            {"/(apps|ads)\\.example\\.(com|org)/",
                                        "ads.example.net",      "/ad.png",              ALLOW},
            {"/(apps|ads)\\.example\\.(com|org)/",
                                        "apps.example.net",     "/ad.png",              ALLOW},

            // selectors
            {"example.com##.banner-ad", "example.com",          "/ad.png",              FILTER},

            // putting it all together
            {"||example.com^$domain=~foo.example.com|~bar.example.com##.banner-ad",
                    "example.com",          "/some_path",      FILTER},
            {"||example.com^$domain=~foo.example.com|~bar.example.com##.banner-ad",
                    "baz.example.com",      "/some_path",      FILTER},
            {"||example.com^$domain=~foo.example.com|~bar.example.com##.banner-ad",
                    "foo.example.com",      "/some_path",      ALLOW},
            {"||example.com^$domain=~foo.example.com|~bar.example.com##.banner-ad",
                    "bar.example.com",      "/some_path",      ALLOW},
    };

    @Test public void testRules () throws Exception {
        for (String[] test : BLOCK_TESTS) {
            final BlockDecisionType expectedDecision = BlockDecisionType.fromString(test[3]);
            final BlockList blockList = new BlockList();
            blockList.addToBlacklist(BlockSpec.parse(test[0]));
            assertEquals("testBlanketBlock: expected "+expectedDecision+" decision, test=" + Arrays.toString(test),
                    expectedDecision,
                    blockList.getDecision(test[1], test[2]).getDecisionType());
        }
    }

    public String[] SELECTOR_SPECS = {
            "||example.com##.banner-ad",
            "||foo.example.com##.more-ads",
    };

    @Test public void testMultipleSelectorMatches () throws Exception {
        final BlockList blockList = new BlockList();
        for (String line : SELECTOR_SPECS) {
            blockList.addToBlacklist(BlockSpec.parse(line));
        }
        BlockDecision decision;

        decision = blockList.getDecision("example.com", "/some_path");
        assertEquals("expected filter decision", BlockDecisionType.filter, decision.getDecisionType());
        assertEquals("expected 1 filter specs", 1, decision.getSpecs().size());

        decision = blockList.getDecision("foo.example.com", "/some_path");
        assertEquals("expected filter decision", BlockDecisionType.filter, decision.getDecisionType());
        assertEquals("expected 2 filter specs", 2, decision.getSpecs().size());
    }
}
