INSERT INTO templates (id, channel, name, subject_pattern, body_pattern, required_vars) VALUES
('email-welcome', 'EMAIL', 'Welcome Email', 'Welcome {{name}}', 'Hi {{name}}, welcome to {{product}}!', 'name,product'),
('sms-otp', 'SMS', 'OTP SMS', 'OTP', 'Your OTP is {{otp}}. Valid for {{minutes}} minutes.', 'otp,minutes'),
('wa-order', 'WHATSAPP', 'Order WhatsApp', 'Order Update', 'Hi {{name}}, order {{orderId}} is {{status}}.', 'name,orderId,status'),
('push-alert', 'PUSH', 'Push Alert', '{{title}}', '{{body}}', 'title,body'),
('slack-deploy', 'SLACK', 'Deploy Slack', 'Deploy {{env}}', 'Service {{service}} deployed to {{env}} by {{actor}}.', 'service,env,actor');
