SET search_path TO finance;

INSERT INTO feed (name, url) VALUES
    ('HLN Economie',       'https://www.hln.be/economie/rss.xml'),
    ('ECB Press Releases', 'https://www.ecb.europa.eu/rss/press.html'),
    ('Politico Europe',    'https://www.politico.eu/feed/')
ON CONFLICT (url) DO NOTHING;
