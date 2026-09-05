// DipScore 스캐폴딩 스모크 테스트.

import 'package:flutter_test/flutter_test.dart';

import 'package:dipscore_frontend/main.dart';

void main() {
  testWidgets('renders hello world', (WidgetTester tester) async {
    await tester.pumpWidget(const DipScoreApp());

    expect(find.text('DipScore'), findsOneWidget);
    expect(find.textContaining('Hello, DipScore!'), findsOneWidget);
  });
}
