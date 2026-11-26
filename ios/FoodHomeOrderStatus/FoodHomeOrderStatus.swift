import ActivityKit
import SwiftUI
import WidgetKit

@main
struct FoodHomeOrderStatus: Widget {
    private let accent = Color(red: 0.99, green: 0.60, blue: 0.25)
    private let displayName = Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? "Food&Home"

    var body: some WidgetConfiguration {
        ActivityConfiguration(for: LiveOrderAttributes.self) { context in
            let progress = context.state.progress
            VStack(alignment: .leading, spacing: 10) {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: context.state.symbol)
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(accent)
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(context.isStale ? "\(displayName) · Последний статус" : "\(displayName) · Мой заказ")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                        Text(context.state.title)
                            .font(.headline)
                            .lineLimit(2)
                    }
                    Spacer(minLength: 4)
                    stageCount(progress)
                }
                .accessibilityElement(children: .combine)
                if let progress {
                    LiveOrderProgressTrack(progress: progress, accent: accent)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
            .widgetURL(context.attributes.orderURL)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: context.state.symbol).foregroundStyle(accent)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(displayName).font(.caption).foregroundStyle(.secondary)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    let progress = context.state.progress
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(context.state.title)
                                .font(.headline)
                                .lineLimit(2)
                            Spacer(minLength: 4)
                            stageCount(progress)
                        }
                        .accessibilityElement(children: .combine)
                        if let progress {
                            LiveOrderProgressTrack(progress: progress, accent: accent)
                        }
                    }
                }
            } compactLeading: {
                Image(systemName: context.state.symbol).foregroundStyle(accent)
                    .accessibilityLabel(context.state.title)
            } compactTrailing: {
                Text(context.state.compactTitle).font(.caption2.bold()).lineLimit(1).minimumScaleFactor(0.75)
            } minimal: {
                Image(systemName: context.state.symbol).foregroundStyle(accent)
                    .accessibilityLabel(context.state.title)
            }
            .widgetURL(context.attributes.orderURL)
            .keylineTint(accent)
        }
    }

    @ViewBuilder
    private func stageCount(_ progress: LiveOrderProgress?) -> some View {
        if let progress, !progress.isFinished {
            Text(progress.stageCountLabel)
                .font(.caption.monospacedDigit().weight(.semibold))
                .foregroundStyle(.secondary)
                .fixedSize()
                .accessibilityHidden(true)
        }
    }
}

private struct LiveOrderProgressTrack: View {
    let progress: LiveOrderProgress
    let accent: Color

    var body: some View {
        HStack(spacing: 0) {
            ForEach(progress.steps.indices, id: \.self) { index in
                let marker = progress.marker(at: index)
                VStack(spacing: 4) {
                    ZStack {
                        HStack(spacing: 0) {
                            connector(reached: index <= progress.currentIndex)
                                .opacity(index == progress.steps.startIndex ? 0 : 1)
                            connector(reached: index < progress.currentIndex)
                                .opacity(index == progress.steps.count - 1 ? 0 : 1)
                        }
                        .frame(height: 2)
                        dot(marker)
                    }
                    .frame(height: 16)

                    Text(progress.title(for: progress.steps[index]))
                        .font(.caption2.weight(marker == .current ? .semibold : .regular))
                        .foregroundStyle(marker == .current ? Color.primary : Color.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .dynamicTypeSize(...DynamicTypeSize.xxLarge)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Этапы заказа")
        .accessibilityValue(progress.accessibilitySummary)
    }

    private func connector(reached: Bool) -> some View {
        Rectangle().fill(reached ? accent : Color.secondary.opacity(0.24))
    }

    private func dot(_ marker: LiveOrderProgress.Marker) -> some View {
        ZStack {
            Circle().fill(.background)
            switch marker {
            case .completed:
                Circle().fill(accent)
                Image(systemName: "checkmark")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundStyle(.black)
            case .current:
                Circle().strokeBorder(accent, lineWidth: 2)
                Circle().fill(accent).frame(width: 6, height: 6)
            case .upcoming:
                Circle().strokeBorder(Color.secondary.opacity(0.6), lineWidth: 1.25)
            }
        }
        .frame(width: 14, height: 14)
    }
}
