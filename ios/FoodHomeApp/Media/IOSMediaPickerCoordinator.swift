import PhotosUI
import UIKit
import UniformTypeIdentifiers

@MainActor
final class IOSMediaPickerCoordinator: NSObject {
    var presenterProvider: (() -> UIViewController?)?

    private let temporaryStore: TemporaryMediaStore
    private var completion: (([URL]?) -> Void)?
    private weak var presentedController: UIViewController?

    init(temporaryStore: TemporaryMediaStore = TemporaryMediaStore()) {
        self.temporaryStore = temporaryStore
        super.init()
        temporaryStore.cleanupStale()
    }

    func present(
        allowsMultipleSelection: Bool,
        sourceView: UIView,
        completion: @escaping ([URL]?) -> Void
    ) {
        finish(nil)
        temporaryStore.cleanupStale()
        guard let presenter = presenterProvider?(), presenter.presentedViewController == nil else {
            completion(nil)
            return
        }
        self.completion = completion

        let sheet = UIAlertController(
            title: "Добавить изображение",
            message: nil,
            preferredStyle: .actionSheet
        )
        sheet.addAction(
            UIAlertAction(title: "Выбрать фото", style: .default) { [weak self] _ in
                DispatchQueue.main.async {
                    self?.presentPhotoPicker(allowsMultipleSelection: allowsMultipleSelection)
                }
            }
        )
        if !allowsMultipleSelection && UIImagePickerController.isSourceTypeAvailable(.camera) {
            sheet.addAction(
                UIAlertAction(title: "Сделать фото", style: .default) { [weak self] _ in
                    DispatchQueue.main.async { self?.presentCamera() }
                }
            )
        }
        sheet.addAction(
            UIAlertAction(title: "Отмена", style: .cancel) { [weak self] _ in
                self?.finish(nil)
            }
        )
        if let popover = sheet.popoverPresentationController {
            popover.sourceView = sourceView
            popover.sourceRect = CGRect(
                x: sourceView.bounds.midX,
                y: sourceView.bounds.midY,
                width: 1,
                height: 1
            )
        }
        presentedController = sheet
        presenter.present(sheet, animated: true)
    }

    func cancelPending() {
        presentedController?.dismiss(animated: false)
        finish(nil)
    }

    private func presentPhotoPicker(allowsMultipleSelection: Bool) {
        guard let presenter = presenterProvider?() else {
            finish(nil)
            return
        }
        var configuration = PHPickerConfiguration(photoLibrary: .shared())
        configuration.filter = .images
        configuration.selectionLimit = allowsMultipleSelection ? 10 : 1
        configuration.preferredAssetRepresentationMode = .current
        let picker = PHPickerViewController(configuration: configuration)
        picker.delegate = self
        presentedController = picker
        presenter.present(picker, animated: true)
    }

    private func presentCamera() {
        guard let presenter = presenterProvider?(),
              UIImagePickerController.isSourceTypeAvailable(.camera)
        else {
            finish(nil)
            return
        }
        let picker = UIImagePickerController()
        picker.delegate = self
        picker.sourceType = .camera
        picker.mediaTypes = [UTType.image.identifier]
        picker.allowsEditing = false
        presentedController = picker
        presenter.present(picker, animated: true)
    }

    private func finish(_ urls: [URL]?) {
        guard let completion else { return }
        self.completion = nil
        presentedController = nil
        completion(urls?.isEmpty == false ? urls : nil)
    }
}

extension IOSMediaPickerCoordinator: PHPickerViewControllerDelegate {
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard !results.isEmpty else {
            finish(nil)
            return
        }

        let group = DispatchGroup()
        let lock = NSLock()
        var selectedURLs: [URL] = []
        for result in results.prefix(10) {
            guard result.itemProvider.hasItemConformingToTypeIdentifier(UTType.image.identifier) else {
                continue
            }
            group.enter()
            result.itemProvider.loadFileRepresentation(
                forTypeIdentifier: UTType.image.identifier
            ) { [temporaryStore] source, _ in
                defer { group.leave() }
                guard let source, let copied = temporaryStore.copyImage(from: source) else { return }
                lock.lock()
                selectedURLs.append(copied)
                lock.unlock()
            }
        }
        group.notify(queue: .main) { [weak self] in
            self?.finish(selectedURLs)
        }
    }
}

extension IOSMediaPickerCoordinator: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
        finish(nil)
    }

    func imagePickerController(
        _ picker: UIImagePickerController,
        didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
        picker.dismiss(animated: true)
        guard let image = info[.originalImage] as? UIImage,
              let data = image.jpegData(compressionQuality: 0.9),
              let url = temporaryStore.writeJPEG(data)
        else {
            finish(nil)
            return
        }
        finish([url])
    }
}
